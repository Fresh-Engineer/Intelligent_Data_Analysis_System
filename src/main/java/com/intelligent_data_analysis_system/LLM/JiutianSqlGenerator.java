package com.intelligent_data_analysis_system.LLM;

import com.intelligent_data_analysis_system.infrastructure.config.properties.RoutingProperties;
import com.intelligent_data_analysis_system.utils.SqlGenResult;
import com.intelligent_data_analysis_system.utils.SqlGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "jiutian")
public class JiutianSqlGenerator implements SqlGenerator {

    private final JiutianChatClient chatClient;
    private final RoutingProperties routingProperties; // 你已有

    @Override
    public SqlGenResult generate(String problem) {
        String domain = "FINANCE"; // domain 建议仍由 runner 传入更好，先不动

        String dialect = toDialect(routingProperties.getDbms()); // "PostgreSQL" / "MySQL"
        String prompt = buildPrompt(problem, domain, dialect);
        String system = """
你是一个Text-to-SQL助手。只输出一条SQL，不要解释。
硬性要求：
- 目标数据库方言：%s
- 只允许 SELECT（只读）
- 单条语句，不要多语句
- 输出必须用 ```sql ... ``` 包裹
""".formatted(dialect);

        String raw = chatClient.chat(system, prompt);  // 不要传 problem

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

    private String buildPrompt(String problem, String domain, String dialect) {
        return """
你是一个Text-to-SQL助手。只输出一条SQL，不要解释。
硬性要求：
- 目标数据库方言：%s
- 只允许 SELECT（只读）
- 单条语句，不要多语句
- 输出必须用 ```sql ... ``` 包裹
问题：%s
""".formatted(dialect, problem);
    }

    private String extractSql(String raw) {
        if (raw == null) return "";
        int s = raw.indexOf("```sql");
        if (s < 0) return "";          // ✅ 不要返回 raw
        int e = raw.indexOf("```", s + 6);
        if (e < 0) return raw.substring(s + 6).trim();
        return raw.substring(s + 6, e).trim();
    }

}
