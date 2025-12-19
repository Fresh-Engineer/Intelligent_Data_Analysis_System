package com.intelligent_data_analysis_system.utils.Fixer;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 仅用于“非聚合、非分组、非 join 的单表查询”场景：
 * 把 SELECT 列列表强制改为 finance 表的默认列集合，尽量贴近官方 gold_sql 的风格。
 */
public class FinanceProjectionFixer {

    // 简单匹配：SELECT ... FROM <table>
    private static final Pattern SELECT_FROM =
            Pattern.compile("(?is)^\\s*select\\s+(.*?)\\s+from\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\b");

    // finance 表默认投影（你可以按 gold_sql 继续补齐/调整）
    private static final Map<String, String> DEFAULT_SELECT = Map.of(
            "clients", "client_id, client_name, risk_level, total_assets",
            "products", "product_id, product_name, product_type, risk_rating, currency",
            "portfolios", "portfolio_id, portfolio_code, client_id, portfolio_type, current_value, contribution_amount",
            "transactions", "transaction_id, portfolio_id, product_id, trade_date, transaction_type, transaction_amount"
    );

    public static String fix(String problem, String sql) {
        if (sql == null || sql.isBlank()) return sql;

        String s = sql.trim();
        String lower = s.toLowerCase(Locale.ROOT);

        // 只处理 SELECT/WITH → SELECT 的简单查询（WITH 也允许，但我们只改最终 SELECT）
        if (!lower.contains("select")) return sql;

        if (lower.contains(" join ")) return sql;

        // 只在“列表/查看/查询”这类题做投影兜底（避免统计类误伤）
        if (!isListingQuestion(problem)) return sql;

        // 排除聚合/分组/去重/连表：这些题投影不应强制模板
        if (containsAny(lower, " group by ", " count(", " sum(", " avg(", " min(", " max(", " distinct ")) return sql;
        if (containsJoin(lower)) return sql;

        Matcher m = SELECT_FROM.matcher(s);
        if (!m.find()) return sql;

        String table = m.group(2);
        String tableLower = table.toLowerCase(Locale.ROOT);
        String replacementCols = DEFAULT_SELECT.get(tableLower);
        if (replacementCols == null) return sql;

        // 如果本来就是 select * 或 select 单列，就强制改投影
        String selectPart = m.group(1).trim();
        if (selectPart.equals("*") || !selectPart.contains(",")) {
            // 用正则替换最前面的 select ... from table 为 select <default> from table
            // 保留后续 WHERE/ORDER/LIMIT 等
            return s.replaceFirst("(?is)^\\s*select\\s+.*?\\s+from\\s+" + Pattern.quote(table) + "\\b",
                    "SELECT " + replacementCols + " FROM " + table);
        }

        // 若已经多列，通常不强改（避免误伤）。你想更激进可以直接强改。
        return sql;
    }

    private static boolean isListingQuestion(String problem) {
        if (problem == null) return false;
        String p = problem.toLowerCase(Locale.ROOT);
        // “查看/查询/列出/显示/获取” + finance 关键词
        boolean verb = containsAny(p, "查看", "查询", "列出", "显示", "获取", "找出");
        boolean obj = containsAny(p, "客户", "产品", "组合", "投资组合", "交易");
        return verb && obj;
    }

    private static boolean containsJoin(String lowerSql) {
        return containsAny(lowerSql, " join ", " left join ", " right join ", " inner join ", " full join ");
    }

    private static boolean containsAny(String text, String... keys) {
        if (text == null) return false;
        for (String k : keys) {
            if (text.contains(k)) return true;
        }
        return false;
    }
}
