package com.intelligent_data_analysis_system.infrastructure.datasource;

import java.util.Set;
import java.util.regex.Pattern;

public class DomainClassifier {

    /** ================= 医疗强信号 ================= */

    // 常见药品（先放最常用的，后续可以慢慢补）
    private static final Set<String> DRUG_NAMES = Set.of(
            "阿莫西林", "阿司匹林", "布洛芬", "头孢", "头孢克肟", "头孢呋辛",
            "奥美拉唑", "二甲双胍", "甲硝唑", "左氧氟沙星", "诺氟沙星",
            "青霉素", "红霉素", "维生素", "维C", "葡萄糖", "胰岛素"
    );

    // 药品通用后缀（命中即医疗）
    private static final Pattern DRUG_SUFFIX_PATTERN = Pattern.compile(
            "(片|胶囊|注射液|针剂|颗粒|滴丸|缓释片|控释片|口服液|混悬液|乳膏|栓|喷雾)"
    );

    // 医疗场景词
    private static final Set<String> MEDICAL_TERMS = Set.of(
            "患者", "病人", "诊断", "处方", "用药", "剂量", "给药", "禁忌",
            "不良反应", "过敏", "适应症", "门诊", "住院", "手术",
            "检验", "检查", "病历", "医保", "科室", "库存", "药房"
    );

    /** ================= 金融强信号 ================= */

    private static final Set<String> FINANCE_TERMS = Set.of(
            "客户", "账户", "资金", "余额", "交易", "持仓", "基金", "股票",
            "债券", "产品", "净值", "收益", "风险", "申购", "赎回",
            "对手方", "结算", "保证金", "投资组合"
    );

    /**
     * 对外主入口：自动识别 Domain
     */
    public static DataSourceDomain detect(String question) {
        if (question == null || question.isBlank()) {
            return DataSourceDomain.FINANCE; // 默认兜底
        }

        String q = normalize(question);

        // 1️⃣ 药品名称直接命中（最强）
        for (String drug : DRUG_NAMES) {
            if (q.contains(drug)) {
                return DataSourceDomain.HEALTHCARE;
            }
        }

        // 2️⃣ 药品后缀命中
        if (DRUG_SUFFIX_PATTERN.matcher(q).find()) {
            return DataSourceDomain.HEALTHCARE;
        }

        // 3️⃣ 医疗语义命中
        for (String term : MEDICAL_TERMS) {
            if (q.contains(term)) {
                return DataSourceDomain.HEALTHCARE;
            }
        }

        // 4️⃣ 金融强信号
        for (String term : FINANCE_TERMS) {
            if (q.contains(term)) {
                return DataSourceDomain.FINANCE;
            }
        }

        // 5️⃣ 都没命中 → 兜底（可以接 LLM，这里先默认金融）
        return DataSourceDomain.FINANCE;
    }

    private static String normalize(String s) {
        return s.replaceAll("\\s+", "")
                .replaceAll("[，。！？,.!?]", "")
                .toLowerCase();
    }
}

