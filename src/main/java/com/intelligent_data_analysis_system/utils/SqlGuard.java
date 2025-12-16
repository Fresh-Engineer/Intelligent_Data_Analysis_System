package com.intelligent_data_analysis_system.utils;

import java.util.Locale;
import java.util.regex.Pattern;

public final class SqlGuard {

    private SqlGuard() {}

    // 允许：SELECT / WITH / EXPLAIN（只读）
    private static final Pattern ALLOW_PREFIX =
            Pattern.compile("^\\s*(select|with|explain)\\b", Pattern.CASE_INSENSITIVE);

    // 禁止关键字
    private static final Pattern FORBIDDEN =
            Pattern.compile("\\b(insert|update|delete|drop|alter|truncate|create|grant|revoke|replace)\\b",
                    Pattern.CASE_INSENSITIVE);

    public static void validateReadOnlySingleStatement(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL不能为空");
        }
        String s = sql.trim();

        // 禁多语句（避免 “; DROP TABLE …”）
        // 允许末尾一个分号：SELECT ...;
        int semicolonCount = 0;
        for (char c : s.toCharArray()) if (c == ';') semicolonCount++;
        if (semicolonCount > 1 || (semicolonCount == 1 && !s.endsWith(";"))) {
            throw new IllegalArgumentException("SQL不允许多语句（仅允许单条查询）");
        }

        // 只读前缀
        if (!ALLOW_PREFIX.matcher(s).find()) {
            throw new IllegalArgumentException("仅允许 SELECT / WITH / EXPLAIN 查询");
        }

        // 禁止危险关键字
        if (FORBIDDEN.matcher(s).find()) {
            throw new IllegalArgumentException("检测到危险SQL关键字，已拒绝执行");
        }
    }

    /**
     * 如果用户 SQL 没写 LIMIT，则自动补一个 LIMIT，防止一次拉爆数据
     * 说明：这是够用的工程策略，不追求 100% 语法完美。
     */
    public static String ensureLimit(String sql, int limit) {
        if (limit <= 0) return sql;

        String s = sql.toLowerCase(Locale.ROOT);
        // 粗判：包含 " limit " 就认为用户已控制行数
        if (s.contains(" limit ")) return sql;

        // 去掉末尾分号再拼
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed + " LIMIT " + limit;
    }
}
