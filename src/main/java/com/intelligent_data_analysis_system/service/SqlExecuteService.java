package com.intelligent_data_analysis_system.service;

import com.intelligent_data_analysis_system.infrastructure.datasource.DomainContext;
import com.intelligent_data_analysis_system.infrastructure.datasource.DataSourceDomain;
import com.intelligent_data_analysis_system.infrastructure.exception.BusinessException;
import com.intelligent_data_analysis_system.infrastructure.runner.dto.QueryResult;
import com.intelligent_data_analysis_system.utils.Generator.SqlGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSetMetaData;
import java.util.*;

@Service
public class SqlExecuteService {

    private static final Logger logger = LoggerFactory.getLogger(SqlExecuteService.class);
    
    private final NamedParameterJdbcTemplate namedJdbc;

    /**
     * 你仓库里已经有 app.routing.dbms（mysql / pgsql），这里直接复用。
     * 没配就默认 mysql。
     */
    @Value("${app.routing.dbms}")
    private String defaultDbms;

    public SqlExecuteService(NamedParameterJdbcTemplate namedJdbc) {
        this.namedJdbc = namedJdbc;
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
                // 确保有LIMIT子句
                String sqlWithLimit = SqlGuard.ensureLimit(sql, maxRows);
                logger.debug("SQL with limit: {}", sqlWithLimit);
                
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

    public QueryResult execute(String domain, String sql, Map<String, ?> params, int limit) {
        QueryResult qr = new QueryResult();
        long t0 = System.currentTimeMillis();

        // 读库路由（你现有的 query 用的是 DataSourceDomain.valueOf(domain)，这里保持一致）
        DomainContext.set(DataSourceDomain.valueOf(domain));
        try {
            if (sql == null || sql.isBlank()) {
                throw new BusinessException("sql 为空");
            }
            SqlGuard.validateReadOnlySingleStatement(sql);

            String safeSql = SqlGuard.ensureLimit(sql, limit);

            ResultSetExtractor<QueryResult> extractor = rs -> {
                ResultSetMetaData md = rs.getMetaData();
                int cc = md.getColumnCount();

                for (int i = 1; i <= cc; i++) {
                    qr.getColumns().add(md.getColumnLabel(i));
                }
                while (rs.next()) {
                    List<Object> row = new ArrayList<>(cc);
                    for (int i = 1; i <= cc; i++) {
                        row.add(rs.getObject(i));
                    }
                    qr.getRows().add(row);
                }
                return qr;
            };

            namedJdbc.query(safeSql, (params == null ? Map.of() : params), extractor);

            qr.setSuccess(true);
            qr.setStatus("success");
            return qr;

        } catch (Exception e) {
            qr.setSuccess(false);
            qr.setStatus("fail");
            qr.setErrorMsg(e.getMessage());
            return qr;
        } finally {
            qr.setElapsedMs(System.currentTimeMillis() - t0);
            DomainContext.clear();
        }
    }

}
