package com.intelligent_data_analysis_system.utils;

import java.util.*;
import java.util.regex.*;

public class ConstraintExtractor {

    private static final Set<String> BAD_VALUES = Set.of("为", "的", "和", "或", "且");

    public static Optional<String> safeWhere(String domain, String problem) {
        if (problem == null) return Optional.empty();
        String p = problem.trim();

        List<String> conds = new ArrayList<>();

        if ("FINANCE".equalsIgnoreCase(domain)) {

            // 风险等级为'成长'
            Matcher m1 = Pattern.compile(
                    "风险等级\\s*为\\s*[\"'“”]?([\\u4e00-\\u9fa5]+)[\"'“”]?"
            ).matcher(p);

            if (m1.find()) {
                String v = m1.group(1);
                if (isValidValue(v)) {
                    conds.add("risk_level = '" + v + "'");
                }
            }

            // 姓王
            Matcher m2 = Pattern.compile("姓([\\u4e00-\\u9fa5])").matcher(p);
            if (m2.find()) {
                conds.add("client_name LIKE '" + m2.group(1) + "%'");
            }

            if (p.contains("活跃") || p.contains("有效")) {
                conds.add("is_active = TRUE");
            }
        }

        if ("HEALTHCARE".equalsIgnoreCase(domain)) {

            if (p.contains("男性")) conds.add("gender = '男'");
            if (p.contains("女性")) conds.add("gender = '女'");

            Matcher m3 = Pattern.compile("年龄.*?(大于|小于)(\\d+)").matcher(p);
            if (m3.find()) {
                conds.add("age " + (m3.group(1).contains("大") ? ">" : "<") + " " + m3.group(2));
            }
        }

        if (conds.isEmpty()) return Optional.empty();
        return Optional.of(String.join(" AND ", conds));
    }

    private static boolean isValidValue(String v) {
        if (v == null) return false;
        if (BAD_VALUES.contains(v)) return false;
        return v.length() <= 6; // 防止“风险等级为成长型客户”这种长串误吸
    }
}
