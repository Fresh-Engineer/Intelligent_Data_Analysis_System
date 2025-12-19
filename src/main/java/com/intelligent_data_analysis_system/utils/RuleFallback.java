package com.intelligent_data_analysis_system.utils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuleFallback {

    public static String tryBuild(String domain, String problem) {
        if (problem == null || problem.isBlank()) return "";

        String p = problem.trim().toLowerCase(Locale.ROOT);

        if ("FINANCE".equalsIgnoreCase(domain)) {
            String sql;

            // 1️⃣ 姓 X 的客户
            sql = financeSurnameClient(p);
            if (!sql.isBlank()) return sql;

            // 2️⃣ 风险等级为 X 的客户
            sql = financeRiskLevelClient(p);
            if (!sql.isBlank()) return sql;

            // 3️⃣ 客户数量
            sql = financeClientCount(p);
            if (!sql.isBlank()) return sql;

            // 4️⃣ 资产前 N 的客户
            sql = financeTopNClients(p);
            if (!sql.isBlank()) return sql;

            // 5️⃣ 列出所有客户
            if (p.contains("客户") && containsAny(p, "列出", "查看", "查询", "显示", "所有")) {
                return """
                    SELECT client_id, client_name, risk_level, total_assets
                    FROM clients
                """.trim();
            }
        }

        if ("HEALTHCARE".equalsIgnoreCase(domain)) {
            String sql;

            // 6️⃣ 列出所有患者
            if (p.contains("患者") && containsAny(p, "列出", "查看", "查询", "显示", "所有")) {
                return """
                    SELECT patient_id, name, gender, age
                    FROM patients
                """.trim();
            }
        }

        return "";
    }

    // ================= finance rules =================

    // 姓 X 的客户
    private static String financeSurnameClient(String p) {
        if (!p.contains("姓") || !p.contains("客户")) return "";

        Matcher m = Pattern.compile("姓([\\u4e00-\\u9fa5])").matcher(p);
        if (!m.find()) return "";

        String surname = m.group(1);

        return """
            SELECT client_id, client_name, risk_level, total_assets
            FROM clients
            WHERE client_name LIKE '%s%%'
        """.formatted(surname).trim();
    }

    // 风险等级为 X 的客户
    private static String financeRiskLevelClient(String p) {
        if (!p.contains("风险") || !p.contains("客户")) return "";

        Matcher m = Pattern.compile("风险等级为?([\\u4e00-\\u9fa5]+)").matcher(p);
        if (!m.find()) return "";

        String level = m.group(1);

        return """
            SELECT client_id, client_name, risk_level, total_assets
            FROM clients
            WHERE risk_level = '%s'
        """.formatted(level).trim();
    }

    // 客户数量
    private static String financeClientCount(String p) {
        if (!p.contains("客户")) return "";
        if (!containsAny(p, "数量", "多少", "统计")) return "";

        return "SELECT COUNT(*) AS cnt FROM clients";
    }

    // 资产前 N 的客户
    private static String financeTopNClients(String p) {
        if (!p.contains("客户")) return "";
        if (!containsAny(p, "前", "top")) return "";

        Matcher m = Pattern.compile("前\\s*(\\d+)").matcher(p);
        if (!m.find()) return "";

        int n = Integer.parseInt(m.group(1));
        if (n <= 0 || n > 1000) n = 10;

        return """
            SELECT client_id, client_name, risk_level, total_assets
            FROM clients
            ORDER BY total_assets DESC
            LIMIT %d
        """.formatted(n).trim();
    }

    // ================= helpers =================

    private static boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k)) return true;
        }
        return false;
    }
}
