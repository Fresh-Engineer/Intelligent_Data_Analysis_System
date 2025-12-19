package com.intelligent_data_analysis_system.utils.Fixer;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HealthcareProjectionFixer {

    private static final Pattern SELECT_FROM =
            Pattern.compile("(?is)^\\s*select\\s+(.*?)\\s+from\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\b");

    // healthcare 表默认投影（按常见 gold_sql 风格）
    private static final Map<String, String> DEFAULT_SELECT = Map.of(
            "patients", "patient_id, name, gender, age",
            "medical_encounters", "encounter_id, patient_id, department_id, encounter_date",
            "departments_wards", "dept_ward_id, name, parent_id",
            "medical_orders", "order_id, patient_id, order_type, order_date",
            "billing_transactions", "billing_id, patient_id, amount, billing_date"
    );

    public static String fix(String problem, String sql) {
        if (sql == null || sql.isBlank()) return sql;

        String s = sql.trim();
        String lower = s.toLowerCase(Locale.ROOT);

        if (!isListingQuestion(problem)) return sql;

        // 排除聚合 / 分组 / join
        if ((containsAny(lower, " group by ", " count(", " sum(", " avg(", " min(", " max(", " distinct "))|| containsJoin(lower)) {
            return sql;
        }

        Matcher m = SELECT_FROM.matcher(s);
        if (!m.find()) return sql;

        String table = m.group(2);
        String cols = DEFAULT_SELECT.get(table.toLowerCase(Locale.ROOT));
        if (cols == null) return sql;

        String selectPart = m.group(1).trim();
        if (selectPart.equals("*") || !selectPart.contains(",")) {
            return s.replaceFirst(
                    "(?is)^\\s*select\\s+.*?\\s+from\\s+" + Pattern.quote(table) + "\\b",
                    "SELECT " + cols + " FROM " + table
            );
        }

        return sql;
    }

    private static boolean isListingQuestion(String problem) {
        if (problem == null) return false;
        String p = problem.toLowerCase(Locale.ROOT);
        boolean verb = containsAny(p, "查看", "查询", "列出", "显示", "获取", "找出");
        boolean obj = containsAny(p, "患者", "就诊", "科室", "医嘱", "账单");
        return verb && obj;
    }

    private static boolean containsJoin(String lowerSql) {
        return containsAny(lowerSql, " join ", " left join ", " right join ",
                " inner join ", " full join ");
    }

    private static boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k)) return true;
        }
        return false;
    }
}
