package com.intelligent_data_analysis_system.utils;

import com.intelligent_data_analysis_system.service.SqlSelfCheckService;

public class WherePresenceGate {

    public static boolean shouldInject(String problem, SqlSelfCheckService.CheckResult check) {

        // 1️⃣ 结构自检明确提示缺 WHERE
        if (check != null && check.hint != null) {
            String h = check.hint.toLowerCase();
            if (h.contains("missing where") || h.contains("no where")) {
                return true;
            }
        }

        // 2️⃣ 强约束题型关键词（题型级，不是题目级）
        if (problem == null) return false;
        String p = problem;

        return p.contains("为")
                || p.contains("等于")
                || p.contains("是")
                || p.contains("包含")
                || p.contains("姓")
                || p.contains("大于")
                || p.contains("小于")
                || p.contains("介于")
                || p.contains("之前")
                || p.contains("之后");
    }
}
