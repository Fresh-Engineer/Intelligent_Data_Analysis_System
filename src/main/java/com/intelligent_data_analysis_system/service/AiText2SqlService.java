package com.intelligent_data_analysis_system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelligent_data_analysis_system.LLM.JiutianSqlGenerator;
import com.intelligent_data_analysis_system.LLM.QWenSqlGenerator;
import com.intelligent_data_analysis_system.infrastructure.exception.BusinessException;
import com.intelligent_data_analysis_system.utils.Generator.SqlGenResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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

    private final ObjectMapper mapper;     // 用 Spring 注入的 ObjectMapper
    private final QWenSqlGenerator sqlGenerator; // 注入接口：qwen/jiutian 由条件化实现决定
    private final JiutianSqlGenerator jiutianSqlGenerator;

    public Map<String, Object> nl2sql(String question) {
        String domain = routeDomainByKeywordEnum(question); // FINANCE/HEALTHCARE
        String dbms = defaultDbms;
        return nl2sql(question, domain, dbms);
    }

    private static final Set<String> DRUG_NAMES = Set.of(
            "阿莫西林", "阿司匹林", "布洛芬", "头孢", "头孢克肟", "头孢呋辛",
            "奥美拉唑", "二甲双胍", "甲硝唑", "左氧氟沙星", "诺氟沙星",
            "青霉素", "红霉素", "胰岛素", "葡萄糖", "维生素"
    );

    private static final Pattern DRUG_SUFFIX = Pattern.compile(
            "(片|胶囊|注射液|针剂|颗粒|滴丸|缓释片|控释片|口服液|混悬液|乳膏|栓|喷雾)"
    );

    private String routeDomainByKeywordEnum(String q) {
        String s = (q == null ? "" : q).trim();
        if (s.isEmpty()) return "FINANCE";

        // 不要只 lower-case：药名是中文，lowerCase 不影响，但这里顺便把空白/标点去掉更稳
        String norm = s.replaceAll("\\s+", "")
                .replaceAll("[，。！？,.!?；;:：()（）\\[\\]{}【】\"“”'’]", "")
                .toLowerCase(Locale.ROOT);

        // 1) ✅ 药名强命中：阿莫西林/阿司匹林等 → 必走医疗
        for (String drug : DRUG_NAMES) {
            if (norm.contains(drug)) {
                return "HEALTHCARE";
            }
        }

        // 2) ✅ 药品形态后缀命中：xx片/xx胶囊/xx注射液 → 医疗
        if (DRUG_SUFFIX.matcher(norm).find()) {
            return "HEALTHCARE";
        }

        // 3) ✅ 医疗关键词（你原来的 + 补强一些高频）
        if (norm.contains("患者") || norm.contains("病人") || norm.contains("就诊") || norm.contains("科室")
                || norm.contains("病历") || norm.contains("医院") || norm.contains("处方")
                || norm.contains("用药") || norm.contains("剂量") || norm.contains("给药")
                || norm.contains("药品") || norm.contains("药房") || norm.contains("库存")
                || norm.contains("检验") || norm.contains("检查") || norm.contains("医生")
                || norm.contains("医师") || norm.contains("手术") || norm.contains("诊断")
                || norm.contains("住院") || norm.contains("门诊") || norm.contains("医") || norm.contains("护士")) {
            return "HEALTHCARE";
        }

        // 4) 金融命中（你的原逻辑）
        if (norm.contains("资产") || norm.contains("客户") || norm.contains("交易") || norm.contains("基金")
                || norm.contains("理财") || norm.contains("持仓") || norm.contains("对手方")
                || norm.contains("净值") || norm.contains("收益") || norm.contains("风险")
                || norm.contains("申购") || norm.contains("赎回") || norm.contains("账户")) {
            return "FINANCE";
        }

        // 5) 默认兜底
        return "FINANCE";
    }



    public Map<String, Object> nl2sql(String question, String domain, String dbms) {
        int maxRows = 200;

        // LLM 模式：直接走当前注入的 generator（qwen/jiutian）
        if ("qwen".equalsIgnoreCase(aiMode)) {
            SqlGenResult gen = sqlGenerator.generate(domain, question);

            Map<String, Object> plan = new LinkedHashMap<>();
            plan.put("domain", normalizeDomainEnumName(gen.getDomain())); // 防御：把 finance -> FINANCE
            plan.put("dbms", dbms);
            plan.put("sql", gen.getSql());
            plan.put("maxRows", maxRows);
            return plan;
        }
        if ("jiutian".equalsIgnoreCase(aiMode)){
            SqlGenResult gen = jiutianSqlGenerator.generate(domain, question);

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
