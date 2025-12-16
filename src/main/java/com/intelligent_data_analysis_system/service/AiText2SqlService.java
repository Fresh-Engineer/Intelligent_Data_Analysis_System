package com.intelligent_data_analysis_system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AiText2SqlService {

    @Value("${app.routing.dbms:mysql}")
    private String defaultDbms;

    @Value("${app.ai.mode:stub}")   // stub | llm
    private String aiMode;

    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Object> nl2sql(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question不能为空");
        }

        // 1) 选 domain（先用关键词路由，后面可让 LLM 输出）
        String domain = routeDomainByKeyword(question);

        // 2) dbms 默认走你已有配置
        String dbms = defaultDbms;

        // 3) schema hint（先最简；后面可自动从信息表读）
        String schema = getSchemaHint(domain);

        // 4) prompt（未来 llm 模式用）
        String prompt = buildPrompt(question, domain, dbms, schema);

        // 5) 调用 LLM（现在 stub，未来接九天）
        String out = callLLM(prompt, question, domain, dbms);

        // 6) 解析为 Map（要求单行 JSON）
        Map<String, Object> plan = parseJsonSafely(out);

        // 兜底补全
        plan.putIfAbsent("domain", domain);
        plan.putIfAbsent("dbms", dbms);
        plan.putIfAbsent("maxRows", 200);

        return plan;
    }

    private String routeDomainByKeyword(String q) {
        String s = q.toLowerCase(Locale.ROOT);
        if (s.contains("资产") || s.contains("产品") || s.contains("客户") || s.contains("交易") || s.contains("组合")) {
            return "finance";
        }
        if (s.contains("患者") || s.contains("就诊") || s.contains("科室") || s.contains("病历") || s.contains("医院")) {
            return "healthcare";
        }
        return "finance";
    }

    private String getSchemaHint(String domain) {
        if ("healthcare".equalsIgnoreCase(domain)) {
            return """
            Tables:
            - patients(patient_id, name, gender, age, ...)
            - visits(visit_id, patient_id, visit_date, dept_id, ...)
            - departments(dept_id, dept_name, parent_id, ...)
            """;
        }
        return """
        Tables:
        - clients(client_id, client_name, client_type, risk_level, total_assets, status, ...)
        - products(product_id, product_name, product_type, risk_rating, currency, ...)
        - portfolios(portfolio_id, portfolio_code, client_id, portfolio_type, current_value, contribution_amount, ...)
        - transactions(transaction_id, portfolio_id, product_id, trade_date, transaction_type, transaction_amount, ...)
        """;
    }

    private String buildPrompt(String question, String domain, String dbms, String schema) {
        return """
        You are a text-to-SQL engine for an analytics system.

        Output MUST be a single-line JSON object with keys: domain, dbms, sql, maxRows.
        sql MUST be read-only (SELECT/WITH/EXPLAIN), no semicolons.
        Use ONLY tables/columns from schema, do NOT invent columns.

        Target domain: %s
        Target dbms: %s

        Schema:
        %s

        User question:
        %s
        """.formatted(domain, dbms, schema, question);
    }

    private String callLLM(String prompt, String question, String domain, String dbms) {
        // ✅ 现在没 token：默认 stub
        if (!"llm".equalsIgnoreCase(aiMode)) {
            return stubToJson(question, domain, dbms);
        }

        // TODO：等官方给九天 token/API，把这里替换成真实调用即可
        // return realLlmCall(prompt);

        // 兜底：避免 llm 模式误开导致不可用
        return stubToJson(question, domain, dbms);
    }

    private String stubToJson(String question, String domain, String dbms) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);

        String sql;
        int maxRows = 200;

        if ("healthcare".equalsIgnoreCase(domain)) {
            if (q.contains("数量") || q.contains("多少") || q.contains("统计")) {
                sql = "select count(*) as cnt from patients";
                maxRows = 50;
            } else {
                sql = "select * from patients";
                maxRows = 50;
            }
        } else {
            if (q.contains("客户") && (q.contains("数量") || q.contains("统计") || q.contains("多少"))) {
                sql = "select count(*) as cnt from clients";
                maxRows = 50;
            } else if (q.contains("前") && (q.contains("资产") || q.contains("总资产"))) {
                sql = "select client_id, client_name, total_assets from clients order by total_assets desc";
                maxRows = 10;
            } else {
                sql = "select 1 as ok";
                maxRows = 1;
            }
        }

        return String.format(Locale.ROOT,
                "{\"domain\":\"%s\",\"dbms\":\"%s\",\"sql\":\"%s\",\"maxRows\":%d}",
                domain, dbms, escapeJson(sql), maxRows);
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonSafely(String text) {
        try {
            return mapper.readValue(text, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("LLM输出不是合法JSON（需要单行JSON）。输出=" + text, e);
        }
    }
}
