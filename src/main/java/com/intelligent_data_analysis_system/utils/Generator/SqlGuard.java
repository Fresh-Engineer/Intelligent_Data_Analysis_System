package com.intelligent_data_analysis_system.utils.Generator;

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
     * 使用JSqlParser确保在正确的位置添加LIMIT子句
     */
    public static String ensureLimit(String sql, int limit) {
        if (limit <= 0) return sql;
        if (sql == null || sql.trim().isEmpty()) return sql;

        // 去掉末尾分号
        String trimmed = sql.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }

        try {
            // 使用更高级的解析来正确添加LIMIT
            net.sf.jsqlparser.statement.select.Select select = (net.sf.jsqlparser.statement.select.Select) net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(trimmed);
            
            // 检查是否是普通SELECT语句
            if (select.getSelectBody() instanceof net.sf.jsqlparser.statement.select.PlainSelect plainSelect) {
                // 检查是否已经有LIMIT子句
                if (plainSelect.getLimit() == null) {
                    // 创建新的LIMIT子句
                    net.sf.jsqlparser.statement.select.Limit newLimit = new net.sf.jsqlparser.statement.select.Limit();
                    newLimit.setRowCount(new net.sf.jsqlparser.expression.LongValue((long) limit));
                    plainSelect.setLimit(newLimit);
                } else {
                    // 已经有LIMIT，确保不超过指定的limit
                    net.sf.jsqlparser.statement.select.Limit existingLimit = plainSelect.getLimit();
                    if (existingLimit.getRowCount() != null) {
                        long existingRowCount = 0;
                        if (existingLimit.getRowCount() instanceof net.sf.jsqlparser.expression.LongValue) {
                            existingRowCount = ((net.sf.jsqlparser.expression.LongValue) existingLimit.getRowCount()).getValue();
                        }
                        if (existingRowCount > limit) {
                            existingLimit.setRowCount(new net.sf.jsqlparser.expression.LongValue((long) limit));
                        }
                    } else {
                        existingLimit.setRowCount(new net.sf.jsqlparser.expression.LongValue((long) limit));
                    }
                }
                return select.toString();
            } 
            // 检查是否是集合操作（如UNION、INTERSECT、EXCEPT等）
            else if (select.getSelectBody() instanceof net.sf.jsqlparser.statement.select.SetOperationList) {
                // 对于集合操作，简单处理：直接在末尾添加LIMIT
                // 因为JSqlParser 4.9版本可能不支持SetOperationList的LIMIT操作
                String s = trimmed.toLowerCase(Locale.ROOT);
                if (!s.contains(" limit ")) {
                    return trimmed + " LIMIT " + limit;
                }
                return trimmed;
            }
            else {
                // 其他类型的SELECT语句，简单处理
                String s = trimmed.toLowerCase(Locale.ROOT);
                if (!s.contains(" limit ")) {
                    return trimmed + " LIMIT " + limit;
                }
                return trimmed;
            }
        } catch (Exception e) {
            // 如果解析或修改失败，回退到简单方式
            String s = trimmed.toLowerCase(Locale.ROOT);
            // 检查是否已经包含LIMIT
            if (!s.contains(" limit ")) {
                // 检查是否是UNION等集合操作，确保LIMIT添加在正确的位置
                if (s.contains(" union ") || s.contains(" intersect ") || s.contains(" except ")) {
                    // 对于集合操作，尝试将LIMIT添加在最后一个语句的末尾
                    // 简单处理：找到最后一个UNION/INTERSECT/EXCEPT并在其后添加LIMIT
                    // 或者直接在整个语句末尾添加LIMIT（PostgreSQL支持这种语法）
                    return trimmed + " LIMIT " + limit;
                } else {
                    // 普通语句，直接在末尾添加LIMIT
                    return trimmed + " LIMIT " + limit;
                }
            }
            return trimmed;
        }
    }
}
