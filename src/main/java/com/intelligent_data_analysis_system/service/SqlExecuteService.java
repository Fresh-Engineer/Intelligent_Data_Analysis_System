package com.intelligent_data_analysis_system.service;

import com.intelligent_data_analysis_system.infrastructure.datasource.DomainContext;
import com.intelligent_data_analysis_system.infrastructure.datasource.DataSourceDomain;
import com.intelligent_data_analysis_system.infrastructure.exception.BusinessException;
import com.intelligent_data_analysis_system.utils.Generator.SqlGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.*;

@Service
public class SqlExecuteService {

    private static final Logger logger = LoggerFactory.getLogger(SqlExecuteService.class);

    private final NamedParameterJdbcTemplate namedJdbc;
    private final MongoTemplate financeMongoTemplate;
    private final MongoTemplate healthcareMongoTemplate;

    /**
     * 你仓库里已经有 app.routing.dbms（mysql / pgsql），这里直接复用。
     * 没配就默认 mysql。
     */
    @Value("${app.routing.dbms}")
    private String defaultDbms;

    public SqlExecuteService(NamedParameterJdbcTemplate namedJdbc,
                             @Qualifier("financeMongoTemplate") MongoTemplate financeMongoTemplate,
                             @Qualifier("healthcareMongoTemplate") MongoTemplate healthcareMongoTemplate) {
        this.namedJdbc = namedJdbc;
        this.financeMongoTemplate = financeMongoTemplate;
        this.healthcareMongoTemplate = healthcareMongoTemplate;
    }

    public List<Map<String, Object>> query(String domain, String sql) {
        DomainContext.set(DataSourceDomain.valueOf(domain));
        try {
            return query(domain, sql, Collections.emptyMap());
        } finally {
            DomainContext.clear();
        }
    }

    public List<Map<String, Object>> query(String domain,
                                           String sql,
                                           Map<String, ?> params) {
        DomainContext.set(DataSourceDomain.valueOf(domain));
        try {
            return namedJdbc.queryForList(sql, params);
        } finally {
            DomainContext.clear();
        }
    }


    /**
     * 统一执行入口（给 controller / agent 用）
     * body 支持字段：
     * - domain: "finance" / "healthcare" 或者直接传 enum 名（FINANCE_MYSQL 等）
     * - dbms: "mysql" / "pgsql"（可选；不传就用 app.routing.dbms）
     * - sql:  要执行的 SQL
     * - params: 命名参数（可选）
     * - maxRows: 行数上限（可选，默认 200）
     */
    public Map<String, Object> execute(Map<String, Object> body) {
        Object d = body.get("domain");
        if (d != null) body.put("domain", d.toString().trim().toUpperCase());

        if (body == null) body = Collections.emptyMap();

        String originalSql = asString(body.get("sql"));
        String domain = asString(body.get("domain"));
        String dbms = asString(body.getOrDefault("dbms", defaultDbms));
        int maxRows = asInt(body.get("maxRows"), 200);

        SqlGuard.validateReadOnlySingleStatement(originalSql);

        DataSourceDomain dsDomain = resolveDomain(domain, dbms);

        long t0 = System.currentTimeMillis();
        DomainContext.set(dsDomain);
        try {
            Map<String, Object> params = asMap(body.get("params"));

            // 设置重试次数
            int maxRetries = 3;
            String sql = originalSql;

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                logger.info("Executing SQL for domain: {}, attempt: {}", domain, attempt);
                logger.debug("SQL to execute: {}", sql);

                try {
                    // 确保有 LIMIT 子句
                    String sqlWithLimit = SqlGuard.ensureLimit(sql, maxRows);
                    logger.debug("SQL with limit: {}", sqlWithLimit);

                    // MongoDB 分支：必须在 namedJdbc 之前 return 掉
                    if ("mongodb".equalsIgnoreCase(dbms) || "mongo".equalsIgnoreCase(dbms)) {
                        Map<String, Object> resp = executeOnMongo(dsDomain, sqlWithLimit, maxRows, t0);
                        logger.info("Mongo query executed successfully for domain: {}, rows returned: {}, time: {}ms",
                                domain,
                                ((List<?>) resp.getOrDefault("rows", List.of())).size(),
                                resp.get("elapsedMs"));
                        return resp;
                    }

                    // 尝试执行SQL
                    List<Map<String, Object>> rows =
                            namedJdbc.query(sqlWithLimit, params, new ColumnMapRowMapper());

                    List<String> columns = rows.isEmpty()
                            ? List.of()
                            : new ArrayList<>(rows.get(0).keySet());

                    long elapsed = System.currentTimeMillis() - t0;
                    logger.info("SQL executed successfully for domain: {}, rows returned: {}, time: {}ms",
                            domain, rows.size(), elapsed);

                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("dataSource", dsDomain.name());
                    resp.put("elapsedMs", elapsed);
                    resp.put("columns", columns);
                    resp.put("rows", rows);
                    resp.put("rowCount", rows.size());
                    return resp;
                } catch (Exception e) {
                    logger.warn("SQL execution failed for domain: {}, attempt: {}, error: {}",
                            domain, attempt, e.getMessage());

                    // 如果是最后一次尝试，抛出异常
                    if (attempt == maxRetries) {
                        logger.error("All SQL execution attempts failed for domain: {}, final SQL: {}",
                                domain, sql);
                        throw e;
                    }

                    // 尝试修复SQL
                    String oldSql = sql;
                    sql = repairSqlForExecution(sql, e.getMessage());
                    logger.debug("SQL repair attempt - from: {}, to: {}", oldSql, sql);

                    // 如果修复后的SQL与原始SQL相同，不再重试
                    if (sql.equals(oldSql)) {
                        logger.warn("SQL repair did not change the statement, stopping retry for domain: {}", domain);
                        throw e;
                    }
                }
            }

            // 理论上不会到达这里
            throw new RuntimeException("SQL执行失败，重试次数已耗尽");
        } finally {
            DomainContext.clear();
        }
    }

    /**
     * 根据执行错误修复SQL
     * @param sql 原始SQL
     * @param errorMessage 错误信息
     * @return 修复后的SQL
     */
    private String repairSqlForExecution(String sql, String errorMessage) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }

        String repaired = sql;

        // 根据错误信息进行针对性修复
        errorMessage = errorMessage.toLowerCase();

        // 处理列名歧义错误
        if (errorMessage.contains("column") && errorMessage.contains("ambiguous")) {
            // 尝试添加表名前缀到SELECT列中
            repaired = addTablePrefixToAmbiguousColumns(repaired);
        }

        // 处理表名不存在的错误
        if (errorMessage.contains("table") && errorMessage.contains("doesn't exist")) {
            // 尝试移除可能的schema前缀
            repaired = repaired.replaceAll("[^\\s]+\\.([\\w_]+)", "$1");
            // 尝试修复常见的表名错误
            repaired = fixCommonTableNameErrors(repaired);
        }

        // 处理列名不存在的错误
//        if (errorMessage.contains("column") && errorMessage.contains("doesn't exist")) {
//            // 尝试修复可能的列名拼写错误（简单处理：保留列名结构）
//            repaired = repaired.replaceAll("(?i)SELECT\\s+.*\\s+FROM", "SELECT * FROM");
//        }

        // 处理语法错误
        if (errorMessage.contains("you have an error in your sql syntax")) {
            // 尝试修复常见的语法错误
            repaired = repaired.replaceAll("(?<!['\\w])\\b(\\d{4}-\\d{2}-\\d{2})\\b(?!['\\w])", "'$1'");
            repaired = repaired.replaceAll("'\\b(\\d+(\\.\\d+)?)\\b'", "$1");
            repaired = repaired.replaceAll("=\\s*=", "=");
        }

        // 处理未闭合的引号
        if (errorMessage.contains("unclosed quotation mark")) {
            int quoteCount = repaired.replaceAll("[^']", "").length();
            if (quoteCount % 2 != 0) {
                repaired = repaired + "'";
            }
        }

        return repaired;
    }

    /**
     * 为模糊列添加表名前缀
     */
    private String addTablePrefixToAmbiguousColumns(String sql) {
        // 简单实现：查找FROM子句中的表名，然后为SELECT中的列添加前缀
        String lowerSql = sql.toLowerCase();

        // 提取表名
        String mainTableName = null;

        // 处理FROM子句
        int fromIndex = lowerSql.indexOf(" from ");
        if (fromIndex != -1) {
            String fromPart = sql.substring(fromIndex + 6);
            int whereIndex = fromPart.toLowerCase().indexOf(" where ");
            int joinIndex = fromPart.toLowerCase().indexOf(" join ");
            int orderByIndex = fromPart.toLowerCase().indexOf(" order by ");
            int groupByIndex = fromPart.toLowerCase().indexOf(" group by ");

            // 找到FROM子句的结束位置
            int endIndex = fromPart.length();
            if (whereIndex != -1) endIndex = Math.min(endIndex, whereIndex);
            if (joinIndex != -1) endIndex = Math.min(endIndex, joinIndex);
            if (orderByIndex != -1) endIndex = Math.min(endIndex, orderByIndex);
            if (groupByIndex != -1) endIndex = Math.min(endIndex, groupByIndex);

            String mainTable = fromPart.substring(0, endIndex).trim();
            mainTableName = mainTable.replaceAll("\\s+.*", ""); // 移除别名
        }

        // 如果找到表名，为SELECT列添加表名前缀
        if (mainTableName != null) {
            // 简单处理：只处理没有别名和复杂表达式的情况
            // 找到SELECT和FROM之间的部分
            int selectIndex = lowerSql.indexOf("select");
            int fromIndex2 = lowerSql.indexOf(" from ");
            if (selectIndex != -1 && fromIndex2 != -1) {
                String selectPart = sql.substring(selectIndex + 6, fromIndex2).trim();

                // 检查SELECT部分是否包含函数或复杂表达式
                if (!selectPart.contains("(") && !selectPart.contains(")")) {
                    // 简单情况：只包含列名
                    String[] columns = selectPart.split(",");
                    StringBuilder newSelectPart = new StringBuilder();
                    for (int i = 0; i < columns.length; i++) {
                        String column = columns[i].trim();
                        // 跳过空列
                        if (column.isEmpty()) continue;
                        // 添加表名前缀
                        if (i > 0) newSelectPart.append(", ");
                        newSelectPart.append(mainTableName).append(".").append(column);
                    }
                    // 重新构建SQL
                    return sql.substring(0, selectIndex + 6) + " " + newSelectPart.toString() + " " + sql.substring(fromIndex2);
                }
            }
        }

        // 如果无法处理，返回原始SQL
        return sql;
    }

    /**
     * 修复常见的表名错误
     */
    private String fixCommonTableNameErrors(String sql) {
        // 常见表名映射（根据错误日志中的表名进行调整）
        Map<String, String> tableNameMapping = new HashMap<>();
        tableNameMapping.put("managers", "manager");
        tableNameMapping.put("portfolios", "portfolio");
        tableNameMapping.put("risk_metrics", "risk_metric");
        tableNameMapping.put("departments", "department");

        String result = sql;
        // 替换表名
        for (Map.Entry<String, String> entry : tableNameMapping.entrySet()) {
            String wrong = entry.getKey();
            String correct = entry.getValue();
            // 确保只替换表名，不替换列名
            result = result.replaceAll("(?i)(from|join|on)\\s+" + wrong + "\\s*", "$1 " + correct + " ");
        }

        return result;
    }

    /**
     * 把 (finance/healthcare + mysql/pgsql) 解析成你已有的 DataSourceDomain 枚举。
     * 兼容两种输入：
     * 1) domain 直接传 enum 名：FINANCE_MYSQL / HEALTHCARE_PGSQL ...
     * 2) domain 传 finance/healthcare，dbms 传 mysql/pgsql，然后拼成 FINANCE_MYSQL ...
     */
    private DataSourceDomain resolveDomain(String domain, String dbms) {
        if (domain == null || domain.isBlank()) {
            throw new BusinessException("domain 不能为空（finance/healthcare 或 DataSourceDomain 枚举名）");
        }

        String d = domain.trim().toUpperCase(Locale.ROOT);
        String m = (dbms == null ? "MYSQL" : dbms.trim().toUpperCase(Locale.ROOT));

        // 常见写法：pgsql / postgres / postgresql 都归一到 PGSQL
        if (m.equals("POSTGRES") || m.equals("POSTGRESQL") || m.equals("PG")) {
            m = "PGSQL";
        }

        // 常见写法：mongo / mongodb 都归一到 MONGODB
        if (m.equals("MONGO") || m.equals("MONGODB")) {
            m = "MONGODB";
        }


        // 1) 先尝试直接当 enum
        try {
            return DataSourceDomain.valueOf(d);
        } catch (Exception ignore) {}

        // 2) 再按 finance/healthcare + dbms 拼 enum
        // 例如 FINANCE + MYSQL => FINANCE_MYSQL
        String candidate = d + "_" + m;
        try {
            return DataSourceDomain.valueOf(candidate);
        } catch (Exception e) {
            throw new BusinessException(
                    "无法解析数据源 domain=" + domain + ", dbms=" + dbms +
                            "，请确认 DataSourceDomain 是否包含：" + candidate
            );
        }
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int asInt(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); }
        catch (Exception e) { return def; }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o == null) return Collections.emptyMap();
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> r = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                r.put(String.valueOf(e.getKey()), e.getValue());
            }
            return r;
        }
        throw new BusinessException("params 必须是 JSON object / Map");
    }

    private Map<String, Object> executeOnMongo(
            DataSourceDomain dsDomain,
            String sqlWithLimit,   // 你外面传进来的 sql（可能已经加过 limit，无所谓）
            int maxRows,
            long t0
    ) {
        // 1) 选 MongoTemplate（按域）
        MongoTemplate mt = pickMongoTemplate(dsDomain);

        // 2) 解析 SQL（极简：只识别 FROM + WHERE 里 col='value' 这种等值 AND）
        ParsedSql ps = parseMinimalSelect(sqlWithLimit);

        // 3) 构造 Mongo Query
        Query q = new Query();
        if (!ps.equalsConditions.isEmpty()) {
            List<Criteria> cs = new ArrayList<>();
            for (Map.Entry<String, Object> kv : ps.equalsConditions.entrySet()) {
                cs.add(Criteria.where(kv.getKey()).is(kv.getValue()));
            }
            q.addCriteria(new Criteria().andOperator(cs.toArray(new Criteria[0])));
        }

        // 只做行数限制（先不管 SQL 里写没写 LIMIT）
        q.limit(Math.max(1, maxRows));

        // 4) 执行
        @SuppressWarnings("rawtypes")
        List<Map> docs = mt.find(q, Map.class, ps.collection);

        // 5) 清理 _id 并组装响应（对齐你 JDBC 返回格式）
        for (Map<?, ?> d : docs) d.remove("_id");

        List<String> columns;
        if (docs.isEmpty()) columns = List.of();
        else {
            columns = new ArrayList<>();
            for (Object k : docs.get(0).keySet()) columns.add(String.valueOf(k));
        }

        long elapsed = System.currentTimeMillis() - t0;

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("dataSource", dsDomain.name());
        resp.put("elapsedMs", elapsed);
        resp.put("columns", columns);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List) docs;
        resp.put("rows", rows);
        resp.put("rowCount", rows.size());
        return resp;
    }

    /** 按 dsDomain 选择 finance/healthcare MongoTemplate。 */
    private MongoTemplate pickMongoTemplate(DataSourceDomain dsDomain) {
        String n = dsDomain.name().toUpperCase(Locale.ROOT);
        if (n.contains("FINANCE")) return financeMongoTemplate;
        if (n.contains("HEALTHCARE")) return healthcareMongoTemplate;
        throw new BusinessException("未知 domain: " + dsDomain.name());
    }

    private static class ParsedSql {
        String collection;
        // 只支持等值 AND 条件：col = value
        Map<String, Object> equalsConditions = new LinkedHashMap<>();
    }

    /**
     * 最小 SQL 解析器：
     * 支持：
     *   SELECT ... FROM <table> [WHERE col='x' AND col2=123]
     * 不支持：
     *   JOIN / OR / IN / LIKE / BETWEEN / ORDER BY / GROUP BY / 子查询
     */
    private ParsedSql parseMinimalSelect(String sql) {
        if (sql == null || sql.isBlank()) throw new BusinessException("sql 为空");

        String s = sql.trim().replaceAll("\\s+", " ");
        String upper = s.toUpperCase(Locale.ROOT);

        int fromIdx = upper.indexOf(" FROM ");
        if (fromIdx < 0) throw new BusinessException("Mongo 最小执行器仅支持 SELECT ... FROM ...");

        // 表名
        String tail = s.substring(fromIdx + 6).trim();

        // 截断到 WHERE（如果有）
        String tablePart;
        int whereIdx = tail.toUpperCase(Locale.ROOT).indexOf(" WHERE ");
        if (whereIdx >= 0) tablePart = tail.substring(0, whereIdx).trim();
        else tablePart = tail.trim();

        // tablePart 可能带别名，这里只取第一个 token
        String[] tokens = tablePart.split(" ");
        String table = tokens[0].trim();
        if (table.isEmpty()) throw new BusinessException("无法解析 FROM 表名");

        ParsedSql ps = new ParsedSql();
        ps.collection = table;

        // WHERE 等值 AND
        if (whereIdx >= 0) {
            String wherePart = tail.substring(whereIdx + 7).trim();

            // 去掉后面可能的 LIMIT / ORDER BY（先简单截断）
            wherePart = cutOff(wherePart, " ORDER BY ");
            wherePart = cutOff(wherePart, " LIMIT ");
            wherePart = cutOff(wherePart, " GROUP BY ");
            wherePart = cutOff(wherePart, " HAVING ");

            // 只支持 AND
            String[] conds = wherePart.split("(?i)\\s+AND\\s+");

            for (String cond : conds) {
                cond = cond.trim();
                if (cond.isEmpty()) continue;

                // 支持 col = 'xxx' 或 col='xxx' 或 col = 123
                Matcher m = Pattern.compile("^([a-zA-Z0-9_\\.]+)\\s*=\\s*(.+)$").matcher(cond);
                if (!m.find()) {
                    throw new BusinessException("Mongo 最小执行器 WHERE 仅支持等值条件: " + cond);
                }

                String col = m.group(1).trim();
                String rawVal = m.group(2).trim();

                // 去掉可能的尾部分号
                if (rawVal.endsWith(";")) rawVal = rawVal.substring(0, rawVal.length() - 1).trim();

                Object val = parseLiteral(rawVal);
                // 如果是 t.col 形式，先用 col（不带表别名）
                if (col.contains(".")) col = col.substring(col.lastIndexOf('.') + 1);

                ps.equalsConditions.put(col, val);
            }
        }

        return ps;
    }

    private String cutOff(String s, String keywordUpperWithSpaces) {
        String upper = s.toUpperCase(Locale.ROOT);
        int idx = upper.indexOf(keywordUpperWithSpaces);
        if (idx >= 0) return s.substring(0, idx).trim();
        return s;
    }

    private Object parseLiteral(String raw) {
        raw = raw.trim();

        // 字符串：'xxx' 或 "xxx"
        if ((raw.startsWith("'") && raw.endsWith("'")) || (raw.startsWith("\"") && raw.endsWith("\""))) {
            return raw.substring(1, raw.length() - 1);
        }

        // NULL
        if ("NULL".equalsIgnoreCase(raw)) return null;

        // 整数
        if (raw.matches("[-+]?\\d+")) {
            try { return Long.parseLong(raw); } catch (Exception ignore) {}
        }

        // 小数
        if (raw.matches("[-+]?\\d+\\.\\d+")) {
            try { return Double.parseDouble(raw); } catch (Exception ignore) {}
        }

        // 兜底：按字符串处理
        return raw;
    }

}
