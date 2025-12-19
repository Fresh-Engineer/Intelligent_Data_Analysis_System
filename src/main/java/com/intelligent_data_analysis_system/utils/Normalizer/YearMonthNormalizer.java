package com.intelligent_data_analysis_system.utils.Normalizer;

import com.intelligent_data_analysis_system.utils.SqlWherePatcher;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YearMonthNormalizer {

    private static final Pattern YEAR = Pattern.compile("(20\\d{2})\\s*年");
    private static final Pattern YEARMONTH = Pattern.compile("(20\\d{2})\\s*年\\s*(1[0-2]|0?[1-9])\\s*月");

    public static String apply(
            String dbms,          // mysql / pgsql
            String domain,
            String problem,
            String sql,
            String preferredDateCol // 可空：你可以传表的默认日期列
    ) {
        if (sql == null || sql.isBlank() || problem == null) return sql;

        Integer year = extractYear(problem);
        Integer month = extractMonth(problem);

        if (year == null) return sql; // 没年月，不动

        String dateCol = chooseDateColumn(domain, problem, preferredDateCol);
        if (dateCol == null || dateCol.isBlank()) return sql;

        String cond = buildYearMonthCond(dbms, dateCol, year, month);

        // 已有 where：安全拼 AND；没有 where：加 where
        return SqlWherePatcher.addConditionSafely(sql, cond);
    }

    private static Integer extractYear(String text) {
        Matcher ym = YEARMONTH.matcher(text);
        if (ym.find()) return Integer.parseInt(ym.group(1));

        Matcher y = YEAR.matcher(text);
        if (y.find()) return Integer.parseInt(y.group(1));

        return null;
    }

    private static Integer extractMonth(String text) {
        Matcher ym = YEARMONTH.matcher(text);
        if (ym.find()) return Integer.parseInt(ym.group(2));
        return null;
    }

    private static String buildYearMonthCond(String dbms, String dateCol, int year, Integer month) {
        String d = (dbms == null ? "" : dbms.toLowerCase(Locale.ROOT));
        boolean isMySQL = d.contains("mysql");

        if (isMySQL) {
            if (month != null) {
                return "YEAR(" + dateCol + ") = " + year + " AND MONTH(" + dateCol + ") = " + month;
            }
            return "YEAR(" + dateCol + ") = " + year;
        }

        // PostgreSQL
        if (month != null) {
            return "EXTRACT(YEAR FROM " + dateCol + ") = " + year +
                    " AND EXTRACT(MONTH FROM " + dateCol + ") = " + month;
        }
        return "EXTRACT(YEAR FROM " + dateCol + ") = " + year;
    }

    /**
     * ✅ 这里别做“范围推断”，只选一个默认日期字段
     * 后续你可以按表/意图加优先级。
     */
    private static String chooseDateColumn(String domain, String problem, String preferred) {
        if (preferred != null && !preferred.isBlank()) return preferred;

        // finance 常见
        if ("FINANCE".equalsIgnoreCase(domain)) {
            if (contains(problem, "成立") || contains(problem, "创建") || contains(problem, "成立时间")) return "inception_date";
            if (contains(problem, "交易") || contains(problem, "成交")) return "trade_date";
            return "create_time"; // 兜底
        }

        // healthcare 常见
        if ("HEALTHCARE".equalsIgnoreCase(domain)) {
            if (contains(problem, "就诊") || contains(problem, "门诊") || contains(problem, "住院")) return "encounter_date";
            if (contains(problem, "医嘱") || contains(problem, "开立") || contains(problem, "下单")) return "start_datetime";
            return "create_time";
        }
        return null;
    }

    private static boolean contains(String s, String kw) {
        return s != null && s.contains(kw);
    }
}
