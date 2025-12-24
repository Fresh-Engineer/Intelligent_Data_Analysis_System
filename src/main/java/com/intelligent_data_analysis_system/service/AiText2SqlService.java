package com.intelligent_data_analysis_system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelligent_data_analysis_system.infrastructure.exception.BusinessException;
import com.intelligent_data_analysis_system.utils.Generator.SqlGenResult;
import com.intelligent_data_analysis_system.utils.Generator.SqlGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiText2SqlService {

    private static final String DOMAIN_FINANCE = "FINANCE";
    private static final String DOMAIN_HEALTHCARE = "HEALTHCARE";

    @Value("${app.routing.dbms}")
    private String defaultDbms;

    @Value("${app.ai.mode:stub}")
    private String aiMode;

    private final ObjectMapper mapper;     // ✅ 用 Spring 注入的 ObjectMapper
    private final SqlGenerator sqlGenerator; // ✅ 注入接口：qwen/jiutian 由条件化实现决定

    public Map<String, Object> nl2sql(String question) {
        String domain = routeDomainByKeywordEnum(question); // FINANCE/HEALTHCARE
        String dbms = defaultDbms;
        return nl2sql(question, domain, dbms);
    }

    private String routeDomainByKeywordEnum(String q) {
        String s = (q == null ? "" : q).toLowerCase(Locale.ROOT);

        // 医疗优先命中
        if (s.contains("患者") || s.contains("就诊") || s.contains("科室") || s.contains("病历") || s.contains("医院")
                || s.contains("处方") || s.contains("药品") || s.contains("检验") || s.contains("检查")) {
            return "HEALTHCARE";
        }

        // 金融命中
        if (s.contains("资产") || s.contains("客户") || s.contains("交易") || s.contains("基金")
                || s.contains("理财") || s.contains("持仓") || s.contains("对手方")) {
            return "FINANCE";
        }

        return "FINANCE";
    }


    public Map<String, Object> nl2sql(String question, String domain, String dbms) {
        int maxRows = 200;

        // LLM 模式：直接走当前注入的 generator（qwen/jiutian）
        if ("qwen".equalsIgnoreCase(aiMode) || "jiutian".equalsIgnoreCase(aiMode)) {
            SqlGenResult gen = sqlGenerator.generate(domain, question);

            Map<String, Object> plan = new LinkedHashMap<>();
            plan.put("domain", normalizeDomainEnumName(gen.getDomain())); // 防御：把 finance -> FINANCE
            plan.put("dbms", dbms);
            plan.put("sql", gen.getSql());
            plan.put("maxRows", maxRows);
            return plan;
        }

        // 非 LLM 模式：stub
        try {
            return mapper.readValue(stubToJson(question, domain, dbms), Map.class);
        } catch (Exception e) {
            throw new BusinessException(400, "stub 生成失败: " + e.getMessage());
        }
    }

    // ====== 自动判域：先关键词，后默认 FINANCE ======

    private String detectDomainEnum(String question) {
        if (question == null) return DOMAIN_FINANCE;
        String q = question.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return DOMAIN_FINANCE;

        // 医疗强特征
        String[] hc = {"患者","病人","就诊","挂号","门诊","住院","出院","科室","医生","护士","处方","药品","药房","检验","检查","ct","mri","病历","医嘱","医保","床位","病房"};
        for (String k : hc) if (q.contains(k)) return DOMAIN_HEALTHCARE;

        // 金融强特征
        String[] fin = {"客户","资产","净值","余额","账户","交易","流水","基金","股票","债券","理财","投资","收益","风险","评级","贷款","利率","持仓","对手方","申购","赎回","组合"};
        for (String k : fin) if (q.contains(k)) return DOMAIN_FINANCE;

        return DOMAIN_FINANCE;
    }

    private String normalizeDomainEnumName(String d) {
        if (d == null) return DOMAIN_FINANCE;
        String x = d.trim().toLowerCase(Locale.ROOT);
        if ("finance".equals(x) || "FINANCE".equalsIgnoreCase(d)) return DOMAIN_FINANCE;
        if ("healthcare".equals(x) || "HEALTHCARE".equalsIgnoreCase(d)) return DOMAIN_HEALTHCARE;
        return d.trim().toUpperCase(Locale.ROOT);
    }

    // ====== 你的 stub 逻辑可保留（我这里只示意） ======
    private String stubToJson(String question, String domain, String dbms) {
        String sql = "select 1 as ok";
        int maxRows = 1;
        return String.format(Locale.ROOT,
                "{\"domain\":\"%s\",\"dbms\":\"%s\",\"sql\":\"%s\",\"maxRows\":%d}",
                domain, dbms, escapeJson(sql), maxRows);
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public SqlGenResult rewriteWithHint(
            String domain,
            String problem,
            String badSql,
            String hint
    ) {
        String rewritePrompt = """
            原始问题：
            %s

            之前生成的 SQL：
            %s

            问题说明：
            %s

            请在【保持问题语义不变】的前提下，仅修正 SQL 结构错误。
            要求：
            1. 只输出一条 SELECT SQL
            2. 不要解释
            3. 不要使用 markdown
            """.formatted(problem, badSql, hint);

        // 直接复用你已有的 LLM 生成链路
        return sqlGenerator.generate(domain, rewritePrompt);
    }

}
