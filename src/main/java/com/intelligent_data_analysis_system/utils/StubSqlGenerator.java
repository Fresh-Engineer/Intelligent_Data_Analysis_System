package com.intelligent_data_analysis_system.utils;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.ai.mode", havingValue = "stub", matchIfMissing = true)
public class StubSqlGenerator implements SqlGenerator {

    @Override
    public SqlGenResult generate(String problem) {
        // 没接 LLM 时：先做一个最保守的 stub（不生成 SQL，但能让 runner 产出 results.xlsx）
        // 你也可以在这里逐步加规则：关键词 -> domain + SQL 模板
        String p = problem == null ? "" : problem.toLowerCase();

        String domain = (p.contains("科室") || p.contains("处方") || p.contains("患者")
                || p.contains("药房") || p.contains("住院") || p.contains("医嘱"))
                ? "HEALTHCARE" : "FINANCE";

        return new SqlGenResult(domain, "");
    }
}