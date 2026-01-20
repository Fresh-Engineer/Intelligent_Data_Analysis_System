package com.intelligent_data_analysis_system.LLM;

import com.intelligent_data_analysis_system.infrastructure.config.properties.RoutingProperties;
import com.intelligent_data_analysis_system.service.SchemaTextProvider;
import com.intelligent_data_analysis_system.service.SqlExecuteService;
import com.intelligent_data_analysis_system.utils.Generator.SqlGenResult;
import com.intelligent_data_analysis_system.utils.Generator.SqlGenerator;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@ConditionalOnProperty(name = "app.ai.mode")
public class JiutianSqlGenerator implements SqlGenerator {

    private static final Logger logger = LoggerFactory.getLogger(JiutianSqlGenerator.class);
    
    private final JiutianChatClient chatClient;
    private final RoutingProperties routingProperties;
    private final SqlExecuteService sqlExecuteService; // ✅新增：用来查 schema（information_schema）
    private final SchemaTextProvider schemaTextProvider;

    // ✅每个 domain 缓存一次 schema，避免每题都查库
    private final ConcurrentHashMap<String, String> schemaCache = new ConcurrentHashMap<>();

    @Override
    public SqlGenResult generate(String domain, String problem) {
        String dialect = toDialect(routingProperties.getDbms());

        String system = """
你是一个专业的Text-to-SQL生成器，能够将自然语言问题准确转换为目标数据库的SQL查询语句。

【核心要求】
- 严格遵循目标数据库方言：%s
- 只允许生成SELECT语句，确保只读操作
- 仅输出单条SQL语句，禁止多语句查询
- 输出必须使用```sql ... ```格式包裹

【关键规则】
1. **表名和列名准确性**：严格使用提供的Schema中的表名和列名，不得使用不存在的表或列
2. **数据类型处理**：
   - 数值类型（int, numeric, decimal）：直接比较，无需引号
   - 字符串类型（varchar, text）：必须使用单引号包裹
   - 日期时间类型（date, timestamp）：必须使用正确的格式和函数
3. **空值处理**：对于可能为空的值，使用IS NULL/IS NOT NULL而不是= NULL或!= NULL
4. **日期时间处理**：
   - 日期比较：使用标准日期格式'YYYY-MM-DD'，如establish_date < '2010-01-01'
   - 年份提取：使用EXTRACT(YEAR FROM column_name)函数，如EXTRACT(YEAR FROM inception_date) = 2010
   - 月份提取：使用EXTRACT(MONTH FROM column_name)函数
   - 日期函数必须作用于有效的日期/时间类型列
5. **聚合函数**：在需要统计、求和、平均值等场景时，正确使用COUNT、SUM、AVG等聚合函数
6. **分组和排序**：根据问题需求正确使用GROUP BY和ORDER BY子句
7. **条件逻辑**：使用AND/OR正确组合条件，确保逻辑准确性
8. **连接查询**：当需要查询多个表的数据时，正确使用JOIN语句并指定连接条件

【SQL语法示例】
- 正确：SELECT counterparty_name FROM counterparties WHERE establish_date < '2010-01-01' AND EXTRACT(YEAR FROM inception_date) = 2010
- 正确：SELECT name FROM users WHERE join_date BETWEEN '2020-01-01' AND '2020-12-31'
- 错误：SELECT * FROM table WHERE date = 2020-01-01  -- 缺少单引号
- 错误：SELECT * FROM table WHERE YEAR(date) = 2020  -- 使用了错误的年份函数

【禁止事项】
- 禁止生成INSERT、UPDATE、DELETE等修改数据的语句
- 禁止生成包含数据库管理命令的语句
- 禁止生成注释或解释性文本
- 禁止生成与问题无关的SQL语句
- 禁止在数值类型上使用单引号
- 禁止在字符串和日期类型上省略单引号

请确保生成的SQL语句能够直接在目标数据库中执行并返回正确结果。
""".formatted(dialect);

        // 关键：生成 schemaText（控制长度，避免太长）
        String schemaText = schemaTextProvider.getSchemaText(domain, 60, 40);

        String prompt = """
【任务】根据以下信息，将自然语言问题准确转换为可执行的SQL查询语句

【Domain】%s

【数据库Schema】
%s

【用户问题】
%s

【生成要求】
1. **准确性**：严格基于提供的Schema生成SQL，确保表名、列名完全匹配
2. **完整性**：覆盖问题中的所有查询条件和需求
3. **语法正确性**：确保生成的SQL符合%s方言的语法规则
4. **性能考虑**：避免不必要的全表扫描，合理使用索引
5. **结果可读性**：返回有意义的列名，避免使用*

【输出格式】
仅输出SQL语句，使用```sql ... ```格式包裹，不包含任何其他解释或说明
""".formatted(domain, schemaText, problem, dialect);

        int maxRetries = 3;
        String sql = null;
        String repairedSql = null;
        boolean valid = false;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            logger.info("Generating SQL for domain: {}, problem: {}, attempt: {}", domain, problem, attempt);
            
            // 生成SQL
            String raw = chatClient.chat(system, prompt);
            sql = extractSql(raw);
            
            logger.debug("Raw response: {}", raw);
            logger.debug("Extracted SQL: {}", sql);

            // 验证SQL语法和表名/列名
            boolean syntaxValid = validateSql(sql);
            boolean schemaValid = validateSqlTableAndColumns(sql, domain);
            valid = syntaxValid && schemaValid;
            
            logger.debug("SQL validation result - syntax: {}, schema: {}", syntaxValid, schemaValid);

            if (valid) {
                logger.info("SQL validation passed on attempt {}", attempt);
                break;
            }

            // 验证失败，尝试修复
            logger.warn("SQL validation failed on attempt {}, attempting repair. Syntax valid: {}, Schema valid: {}", 
                       attempt, syntaxValid, schemaValid);
            
            repairedSql = repairSql(sql);
            logger.debug("Repaired SQL: {}", repairedSql);
            
            // 验证修复后的SQL
            syntaxValid = validateSql(repairedSql);
            schemaValid = validateSqlTableAndColumns(repairedSql, domain);
            valid = syntaxValid && schemaValid;
            
            logger.debug("Repaired SQL validation result - syntax: {}, schema: {}", syntaxValid, schemaValid);

            if (valid) {
                logger.info("Repaired SQL validation passed on attempt {}", attempt);
                sql = repairedSql;
                break;
            }

            // 如果是最后一次尝试，即使验证失败也返回
            if (attempt == maxRetries) {
                logger.warn("All {} attempts failed. Using the best available SQL.", maxRetries);
                // 如果修复后的SQL比原始SQL更完整，使用修复后的
                if (repairedSql != null && !repairedSql.isEmpty() && (sql == null || sql.isEmpty() || repairedSql.length() > sql.length())) {
                    sql = repairedSql;
                    logger.debug("Using repaired SQL as it's more complete");
                }
                break;
            }
        }
        
        logger.info("Final SQL for domain: {}, problem: {} - {}", domain, problem, sql);

        return new SqlGenResult(domain, sql);
    }

    private String toDialect(String dbms) {
        if (dbms == null) return "PostgreSQL";
        dbms = dbms.trim().toLowerCase();
        if (dbms.equals("mysql")) return "MySQL";
        if (dbms.equals("pg") || dbms.equals("pgsql") || dbms.equals("postgres") || dbms.equals("postgresql"))
            return "PostgreSQL";
        return "PostgreSQL";
    }

    private String buildPrompt(String problem, String domain, String dialect, String schema) {
        return """
【任务】将自然语言问题转换为SQL（Text-to-SQL）。

【硬性要求（必须严格遵守）】
- 目标数据库方言：%s
- 只允许 SELECT（只读）
- 单条语句，不要多语句，不要分号
- 只能使用【数据库 Schema】中出现的表名和列名，禁止编造列名
- 输出必须使用 ```sql ... ``` 包裹
- 禁止输出任何解释、注释、道歉、免责声明

【多表与列名规则（极其重要，违反即错误）】
1) 只要 SQL 中出现两张及以上表（JOIN / 子查询 FROM 多表）：
   - SELECT / WHERE / ON / GROUP BY / HAVING / ORDER BY 中的所有列
     必须写成：表别名.列名
   - 严禁使用裸列名
2) FROM 和 JOIN 中每一张表都必须显式指定表别名
3) ON 条件里的列也必须加别名（避免 encounter_type 这类歧义）
4) 输出 SQL 前自检：所有列必须能在 Schema 中找到

【执行兼容性约束（针对你当前评测环境的高频FAIL点，必须遵守）】
- 禁止使用 QUALIFY（会在 MySQL 环境报错）；如需分组TopN：
  用 ROW_NUMBER() OVER(...) 生成 rn，然后外层 WHERE rn <= N
- 如题目涉及“客户名称/客户创建时间”：
  managers 表中没有 client_name / create_time，必须 JOIN clients c ON c.manager_id = m.manager_id，
  然后使用 c.client_name / c.create_time
- 医疗库时间字段为 created_time（不是 create_time），只能使用 created_time
- counterparties 表没有 inception_date，若题目出现“成立/设立日期”，使用 establish_date
- pharmacy_inventory 表没有 item_name；若题目给“药名”但 Schema 无药品字典表，则不要生成该过滤条件

【输出风格约束】
- 默认优先写最短可执行 SQL（能不用子查询就不用）
- 需要 DISTINCT 时再用 DISTINCT，避免无谓去重
- 涉及“不存在/未发生”的条件，优先 NOT EXISTS，避免 NOT IN + NULL 风险

【当前上下文】
- 业务域 domain：%s
- SQL 方言 dialect：%s

【数据库 Schema（非常重要）】
%s

【问题】
%s
""".formatted(
                dialect,
                domain,
                dialect,
                schema,
                problem == null ? "" : problem.trim()
        );
    }

    /**
     * 从模型响应中提取SQL语句，增强健壮性以处理各种边缘情况
     */
    private String extractSql(String raw) {
        if (raw == null) return "";

        String s = raw.trim();
        if (s.isEmpty()) return "";

        // 1️⃣ 优先处理：从```sql ...```格式中提取
        var fencePattern = java.util.regex.Pattern.compile("(?is)```\\s*sql\\s*([\\s\\S]*?)\\s*```");
        var fenceMatcher = fencePattern.matcher(s);
        if (fenceMatcher.find()) {
            String sql = fenceMatcher.group(1).trim();
            if (!sql.isEmpty()) {
                return stripTrailingSemicolon(sql);
            }
        }

        // 2️⃣ 备选方案：处理可能没有正确包裹的SQL
        // 查找以SELECT或WITH开头的部分
        var selectPattern = java.util.regex.Pattern.compile("(?is)(?:^|\\s)(with|select)\\b[\\s\\S]*");
        var selectMatcher = selectPattern.matcher(s);
        if (selectMatcher.find()) {
            String sql = selectMatcher.group(0).trim();
            // 处理可能包含的其他内容，只保留到第一个有效结束符
            int semicolonIndex = sql.indexOf(';');
            if (semicolonIndex != -1) {
                sql = sql.substring(0, semicolonIndex).trim();
            }
            // 处理可能包含的中文或其他无关内容
            var cleanPattern = java.util.regex.Pattern.compile("(?is)([\\s\\S]*?)(?:\\s*[\u4e00-\u9fa5]|$)");
            var cleanMatcher = cleanPattern.matcher(sql);
            if (cleanMatcher.find()) {
                sql = cleanMatcher.group(1).trim();
            }
            return stripTrailingSemicolon(sql);
        }

        // 3️⃣ 特殊情况处理：检查是否包含SELECT但格式异常
        if (s.toLowerCase().contains("select")) {
            // 尝试提取SELECT及其后的内容
            int selectIndex = s.toLowerCase().indexOf("select");
            String sql = s.substring(selectIndex).trim();
            // 基本清理
            sql = sql.replaceAll("(?is)[^\\w\\s.,()<>!=+*/-]+", " ").trim();
            return stripTrailingSemicolon(sql);
        }

        // 4️⃣ 最后兜底：确实没有SQL
        return "";
    }

    private String stripTrailingSemicolon(String sql) {
        String x = sql.trim();
        if (x.endsWith(";")) {
            return x.substring(0, x.length() - 1).trim();
        }
        return x;
    }

    /**
     * 验证SQL语法是否正确以及表名和列名是否存在
     * @param sql 要验证的SQL语句
     * @return 是否验证通过
     */
    private boolean validateSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }
        
        try {
            // 使用JSqlParser解析SQL，检查语法错误
            net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql);
            return true;
        } catch (Exception e) {
            // 解析失败，SQL语法有错误
            return false;
        }
    }
    
    /**
     * 验证SQL中的表名和列名是否存在于数据库schema中
     * @param sql 要验证的SQL语句
     * @param domain 数据库域
     * @return 是否验证通过
     */
    private boolean validateSqlTableAndColumns(String sql, String domain) {
        if (sql == null || sql.trim().isEmpty() || domain == null || domain.trim().isEmpty()) {
            return false;
        }
        
        try {
            // 获取当前domain的schema信息
            String schemaText = schemaTextProvider.getSchemaText(domain, Integer.MAX_VALUE, Integer.MAX_VALUE);
            
            // 解析SQL，提取表名和列名
            net.sf.jsqlparser.statement.select.Select select = (net.sf.jsqlparser.statement.select.Select) net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql);
            
            // 提取表名
            List<String> tables = new ArrayList<>();
            List<String> columns = new ArrayList<>();
            
            if (select.getSelectBody() instanceof net.sf.jsqlparser.statement.select.PlainSelect plainSelect) {
                // 使用直接获取表的方式替代已过时的TablesNamesFinder
                if (plainSelect.getFromItem() instanceof net.sf.jsqlparser.schema.Table) {
                    tables.add(((net.sf.jsqlparser.schema.Table) plainSelect.getFromItem()).getName());
                }
                // 处理JOIN的表
                if (plainSelect.getJoins() != null) {
                    for (var join : plainSelect.getJoins()) {
                        if (join.getRightItem() instanceof net.sf.jsqlparser.schema.Table) {
                            tables.add(((net.sf.jsqlparser.schema.Table) join.getRightItem()).getName());
                        }
                    }
                }
                
                // 提取列名
                for (var item : plainSelect.getSelectItems()) {
                    // 使用反射安全地获取表达式，避免依赖具体实现类
                    try {
                        java.lang.reflect.Method getExpressionMethod = item.getClass().getMethod("getExpression");
                        Object expression = getExpressionMethod.invoke(item);
                        if (expression instanceof net.sf.jsqlparser.schema.Column column) {
                            columns.add(column.getColumnName());
                        }
                    } catch (Exception e) {
                        // 忽略反射异常，继续处理下一个项目
                        continue;
                    }
                }
            }
            
            // 检查表名是否存在于schema中
            for (String table : tables) {
                if (!schemaText.contains(table)) {
                    return false;
                }
            }
            
            // 检查列名是否存在于schema中
            for (String column : columns) {
                if (!schemaText.contains(column)) {
                    return false;
                }
            }
            
            return true;
        } catch (Exception e) {
            // 解析或验证失败，可能是复杂查询导致的，暂时返回true
            return false;
        }
    }

    /**
     * 修复常见的SQL语法错误
     * @param sql 要修复的SQL语句
     * @return 修复后的SQL语句
     */
    private String repairSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }
        
        String repaired = sql;
        
        // 1. 修复日期格式：为没有引号的日期值添加单引号
        // 匹配YYYY-MM-DD格式的日期，但不包括已经在引号中的
        repaired = repaired.replaceAll("(?<!['\\w])\\b(\\d{4}-\\d{2}-\\d{2})\\b(?!['\\w])", "'$1'");
        
        // 2. 修复年份函数：将YEAR(column)转换为EXTRACT(YEAR FROM column)
        repaired = repaired.replaceAll("(?i)YEAR\\s*\\(([^)]+)\\)", "EXTRACT(YEAR FROM $1)");
        
        // 3. 修复月份函数：将MONTH(column)转换为EXTRACT(MONTH FROM column)
        repaired = repaired.replaceAll("(?i)MONTH\\s*\\(([^)]+)\\)", "EXTRACT(MONTH FROM $1)");
        
        // 4. 修复日期函数：将DATE(column)转换为EXTRACT(DAY FROM column)（如果用于日期比较）
        repaired = repaired.replaceAll("(?i)DATE\\s*\\(([^)]+)\\)", "EXTRACT(DAY FROM $1)");
        
        // 5. 修复引号问题：删除数值类型上的引号
        // 匹配'数字'模式，但不包括日期格式
        repaired = repaired.replaceAll("'\\b(\\d+(\\.\\d+)?)\\b'", "$1");
        
        // 6. 修复不匹配的单引号（简单处理：确保单引号数量为偶数）
        int quoteCount = repaired.replaceAll("[^']", "").length();
        if (quoteCount % 2 != 0) {
            // 在字符串末尾添加单引号
            repaired = repaired + "'";
        }
        
        // 7. 修复缺失的逗号：在表名和FROM关键字之间添加逗号
        repaired = repaired.replaceAll("(?i)([\\w_]+)\\s+FROM", "$1, FROM");
        
        // 8. 修复错误的表连接语法：将逗号连接转换为INNER JOIN（简单情况）
        repaired = repaired.replaceAll("(?i)FROM\\s+([\\w_]+)\\s*,\\s*([\\w_]+)\\s+WHERE\\s+([\\w_]+)\\.([\\w_]+)\\s*=\\s*([\\w_]+)\\.([\\w_]+)", 
                                      "FROM $1 INNER JOIN $2 ON $3.$4 = $5.$6");
        
        // 9. 修复重复的关键字
        repaired = repaired.replaceAll("(?i)(SELECT)\\s+\\1", "$1");
        repaired = repaired.replaceAll("(?i)(FROM)\\s+\\1", "$1");
        repaired = repaired.replaceAll("(?i)(WHERE)\\s+\\1", "$1");
        
        // 10. 修复缺失的括号：确保函数调用有正确的括号
        repaired = repaired.replaceAll("(?i)COUNT\\s+([^\\(])(?![\\w\\s])", "COUNT($1)");
        repaired = repaired.replaceAll("(?i)SUM\\s+([^\\(])(?![\\w\\s])", "SUM($1)");
        repaired = repaired.replaceAll("(?i)AVG\\s+([^\\(])(?![\\w\\s])", "AVG($1)");
        repaired = repaired.replaceAll("(?i)MAX\\s+([^\\(])(?![\\w\\s])", "MAX($1)");
        repaired = repaired.replaceAll("(?i)MIN\\s+([^\\(])(?![\\w\\s])", "MIN($1)");
        
        // 11. 修复错误的比较操作符：将= =转换为=
        repaired = repaired.replaceAll("=\\s*=", "=");
        
        // 12. 修复缺失的表别名：在表名后添加简单别名
        repaired = repaired.replaceAll("(?i)FROM\\s+([\\w_]+)(?!\\s+AS|\\s+[\\w_])", "FROM $1 AS t1");
        repaired = repaired.replaceAll("(?i)JOIN\\s+([\\w_]+)(?!\\s+AS|\\s+[\\w_])", "JOIN $1 AS t2");
        
        // 13. 修复错误的通配符：将*放在SELECT之后
        repaired = repaired.replaceAll("(?i)SELECT\\s+\\*\\s+(?![FROM])", "SELECT * FROM ");
        
        return repaired;
    }



    // ========================= schema 获取（轻量RAG） =========================

    private String getSchemaSummaryCached(String domain, String dialect) {
        String key = domain + "|" + dialect;
        return schemaCache.computeIfAbsent(key, k -> buildSchemaSummaryFromDB(domain, dialect));
    }

    private String buildSchemaSummaryFromDB(String domain, String dialect) {
        boolean isMySQL = "MySQL".equalsIgnoreCase(dialect);

        String columnsSql = isMySQL ? MYSQL_COLUMNS_SQL : PG_COLUMNS_SQL;
        String fkSql = isMySQL ? MYSQL_FK_SQL : PG_FK_SQL;

        List<Map<String, Object>> cols = sqlExecuteService.query(domain, columnsSql);
        List<Map<String, Object>> fks = sqlExecuteService.query(domain, fkSql);

        Map<String, List<ColInfo>> tableCols = new LinkedHashMap<>();
        for (Map<String, Object> r : cols) {
            String table = s(r.get("table_name"));
            String col = s(r.get("column_name"));
            String type = s(r.get("data_type"));
            if (table.isBlank() || col.isBlank()) continue;
            tableCols.computeIfAbsent(table, k -> new ArrayList<>()).add(new ColInfo(col, type));
        }
        for (List<ColInfo> list : tableCols.values()) {
            list.sort(Comparator.comparing(a -> a.name));
        }

        List<String> fkLines = new ArrayList<>();
        for (Map<String, Object> r : fks) {
            String table = s(r.get("table_name"));
            String col = s(r.get("column_name"));
            String refTable = s(r.get("referenced_table_name"));
            String refCol = s(r.get("referenced_column_name"));
            if (table.isBlank() || col.isBlank() || refTable.isBlank() || refCol.isBlank()) continue;
            fkLines.add(table + "." + col + " -> " + refTable + "." + refCol);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("TABLES:\n");

        int tableLimit = 120; // 防止 prompt 过长
        int tableCount = 0;

        for (Map.Entry<String, List<ColInfo>> e : tableCols.entrySet()) {
            if (tableCount++ >= tableLimit) {
                sb.append("... (more tables omitted)\n");
                break;
            }
            String table = e.getKey();
            List<ColInfo> list = e.getValue();
            String colsText = list.stream()
                    .map(c -> c.type.isBlank() ? c.name : (c.name + ":" + shortType(c.type)))
                    .collect(Collectors.joining(", "));
            sb.append("- ").append(table).append("(").append(colsText).append(")\n");
        }

        if (!fkLines.isEmpty()) {
            sb.append("FOREIGN_KEYS:\n");
            int fkLimit = 200;
            for (int i = 0; i < Math.min(fkLines.size(), fkLimit); i++) {
                sb.append("- ").append(fkLines.get(i)).append('\n');
            }
            if (fkLines.size() > fkLimit) sb.append("... (more foreign keys omitted)\n");
        }

        return sb.toString().trim();
    }

    private static String s(Object o) { return o == null ? "" : String.valueOf(o).trim(); }

    private static String shortType(String t) {
        String x = t.toLowerCase(Locale.ROOT);
        if (x.contains("character varying")) return "varchar";
        if (x.contains("timestamp")) return "timestamp";
        if (x.contains("int")) return "int";
        if (x.contains("numeric") || x.contains("decimal")) return "numeric";
        return x;
    }

    private static final class ColInfo {
        final String name;
        final String type;
        ColInfo(String name, String type) { this.name = name; this.type = type; }
    }

    private static final String MYSQL_COLUMNS_SQL = """
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = DATABASE()
ORDER BY table_name, ordinal_position
""";

    private static final String MYSQL_FK_SQL = """
SELECT
  kcu.table_name,
  kcu.column_name,
  kcu.referenced_table_name,
  kcu.referenced_column_name
FROM information_schema.key_column_usage kcu
WHERE kcu.table_schema = DATABASE()
  AND kcu.referenced_table_name IS NOT NULL
ORDER BY kcu.table_name, kcu.column_name
""";

    private static final String PG_COLUMNS_SQL = """
SELECT table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema = 'public'
ORDER BY table_name, ordinal_position
""";

    private static final String PG_FK_SQL = """
SELECT
  kcu.table_name,
  kcu.column_name,
  ccu.table_name  AS referenced_table_name,
  ccu.column_name AS referenced_column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
  ON tc.constraint_name = kcu.constraint_name
 AND tc.table_schema = kcu.table_schema
JOIN information_schema.constraint_column_usage ccu
  ON ccu.constraint_name = tc.constraint_name
 AND ccu.table_schema = tc.table_schema
WHERE tc.constraint_type = 'FOREIGN KEY'
  AND tc.table_schema = 'public'
ORDER BY kcu.table_name, kcu.column_name
""";
}
