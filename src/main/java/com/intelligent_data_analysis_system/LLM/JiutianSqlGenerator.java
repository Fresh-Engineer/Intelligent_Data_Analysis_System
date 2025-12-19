package com.intelligent_data_analysis_system.LLM;

import com.intelligent_data_analysis_system.infrastructure.config.properties.RoutingProperties;
import com.intelligent_data_analysis_system.service.SchemaTextProvider;
import com.intelligent_data_analysis_system.service.SqlExecuteService;
import com.intelligent_data_analysis_system.utils.Generator.SqlGenResult;
import com.intelligent_data_analysis_system.utils.Generator.SqlGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "jiutian")
public class JiutianSqlGenerator implements SqlGenerator {

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
你是一个 Text-to-SQL 生成器。只输出一条SQL，不要解释。
硬性要求：
- 目标数据库方言：%s
- 只允许 SELECT（只读）
- 单条语句，不要多语句
- 输出必须用 ```sql ... ``` 包裹
""".formatted(dialect);

        // 关键：生成 schemaText（控制长度，避免太长）
        String schemaText = schemaTextProvider.getSchemaText(domain, 60, 40);

        String prompt = """
【任务】根据问题生成一条可执行 SQL
【Domain】%s
【Schema】
%s

【问题】
%s

【输出要求】
- 只输出一条 SQL
- 只读 SELECT
- 严格使用 Schema 中的表/字段
- 输出必须用 ```sql ``` 包裹
""".formatted(domain, schemaText, problem);

        String raw = chatClient.chat(system, prompt);
        String sql = extractSql(raw);

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

【硬性要求】
- 目标数据库方言：%s
- 只允许 SELECT（只读）
- 单条语句，不要多语句，不要分号
- 只能使用给定 schema 中出现的表名/列名
- 输出必须使用 ```sql ... ``` 包裹
- 禁止输出任何解释、注释、道歉、免责声明

【数据库 Schema（非常重要）】
%s

【问题】
%s
""".formatted(dialect, schema, problem == null ? "" : problem.trim());
    }

    /**
     * ✅只接受 ```sql ...```，否则返回空字符串，让上层判定 pred_sql为空（避免把“抱歉”当SQL）
     */
    private String extractSql(String raw) {
        if (raw == null) return "";

        String s = raw.trim();

        // 1️⃣ 优先：抓 ```sql fenced```
        var fence = java.util.regex.Pattern
                .compile("(?is)```\\s*sql\\s*(.*?)```")
                .matcher(s);
        if (fence.find()) {
            String sql = fence.group(1).trim();
            if (!sql.isEmpty()) {
                return stripTrailingSemicolon(sql);
            }
        }

        // 2️⃣ 兜底：抓第一段 SELECT / WITH
        var m = java.util.regex.Pattern
                .compile("(?is)\\b(with|select)\\b[\\s\\S]*")
                .matcher(s);
        if (m.find()) {
            String sql = m.group(0).trim();
            return stripTrailingSemicolon(sql);
        }

        // 3️⃣ 最后兜底：真的没有 SQL
        return "";
    }

    private String stripTrailingSemicolon(String sql) {
        String x = sql.trim();
        if (x.endsWith(";")) {
            return x.substring(0, x.length() - 1).trim();
        }
        return x;
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
