package com.intelligent_data_analysis_system.utils.Normalizer;

import com.intelligent_data_analysis_system.mapping.MappingRegistry;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnumValueNormalizer {

    // 支持 alias.col = 'xxx' 以及 col = 'xxx'
    // group1: alias(可选)  group2: col  group3: quote  group4: val
    private static final Pattern EQ_STR =
            Pattern.compile("(?is)(?:(\\b[a-zA-Z_][a-zA-Z0-9_]*\\b)\\.)?(\\b[a-zA-Z_][a-zA-Z0-9_]*\\b)\\s*=\\s*(['\"])([^'\"]+)\\3");

    public static String normalize(String domain, String sql) {
        if (sql == null || sql.isBlank()) return sql;

        // ✅ 只构建一次：alias -> table
        Map<String, String> alias2table = buildAliasToTable(sql);
        String defaultTable = inferTable(sql);

        Matcher m = EQ_STR.matcher(sql);
        StringBuffer sb = new StringBuffer();

        while (m.find()) {
            String alias = m.group(1);  // may be null
            String col   = m.group(2);
            String quote = m.group(3);
            String val   = m.group(4);

            // 1) 计算 fullCol（table.col）
            String table = null;
            if (alias != null) table = alias2table.get(alias);
            if (table == null || table.isBlank()) table = defaultTable;

            String fullCol = (table == null || table.isBlank())
                    ? col
                    : (table + "." + col);

            String newVal = val;

            // 2) 最优先：MappingRegistry 精确映射
            String mapped = MappingRegistry.get()
                    .mapValue(domain, fullCol, val)
                    .orElse(null);

            if (mapped != null) {
                newVal = mapped;
            } else {
                // 3) 布尔拼写兜底（不处理 1/0，把 1/0 留给 mapping）
                String bool = normalizeBooleanWord(val);
                if (bool != null) {
                    newVal = bool;
                } else {
                    // 4) 英文→中文启发式兜底（低优先级）
                    newVal = heuristicEnglishEnum(val);
                }
            }

            // ✅ 不要强行加引号：TRUE/FALSE/数字 -> 原样；其它保留原引号
            String replacement = renderReplacement(col, newVal, quote);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }

        m.appendTail(sb);
        return sb.toString();
    }

    /** ========== 解析 alias -> table（FROM/JOIN） ========== */
    private static Map<String, String> buildAliasToTable(String sql) {
        Map<String, String> map = new HashMap<>();
        if (sql == null) return map;

        Pattern p = Pattern.compile(
                "(?is)\\b(from|join)\\s+([a-zA-Z_][a-zA-Z0-9_]*)(?:\\s+(?:as\\s+)?([a-zA-Z_][a-zA-Z0-9_]*))?"
        );
        Matcher m = p.matcher(sql);
        while (m.find()) {
            String table = m.group(2);
            String alias = m.group(3);

            if (table != null && !table.isBlank()) {
                map.put(table, table); // 无别名也可用
            }
            if (alias != null && !alias.isBlank() && table != null && !table.isBlank()) {
                map.put(alias, table);
            }
        }
        return map;
    }

    /** ========== 默认表：取第一个 FROM 表名 ========== */
    private static String inferTable(String sql) {
        Matcher m = Pattern.compile("(?is)\\bfrom\\s+([a-zA-Z_][a-zA-Z0-9_]*)").matcher(sql);
        if (m.find()) return m.group(1);
        return "";
    }

    /** ========== 布尔词兜底：只处理 yes/no/true/false/y/n 以及 ture 拼写 ========== */
    private static String normalizeBooleanWord(String v) {
        if (v == null) return null;
        String x = v.trim().toLowerCase(Locale.ROOT);
        if (x.equals("ture")) x = "true";

        if (x.equals("y") || x.equals("yes") || x.equals("true")) return "TRUE";
        if (x.equals("n") || x.equals("no")  || x.equals("false")) return "FALSE";
        return null;
    }

    /** ========== 英文枚举兜底（低优先级） ========== */
    private static String heuristicEnglishEnum(String v) {
        if (v == null) return null;
        return switch (v.trim().toLowerCase(Locale.ROOT)) {
            case "bank" -> "银行";
            case "broker", "securities" -> "券商";
            case "insurance" -> "保险";
            case "sell" -> "卖出";
            case "buy" -> "买入";
            case "confirmed" -> "已成";
            default -> v;
        };
    }

    /** ========== 生成最终 col = literal（数字/TRUE/FALSE 不加引号） ========== */
    private static String renderReplacement(String col, String newVal, String originalQuote) {
        return col + " = " + renderLiteral(newVal, originalQuote);
    }

    private static String renderLiteral(String v, String originalQuote) {
        if (v == null) return originalQuote + originalQuote;

        String x = v.trim();

        // TRUE/FALSE：不加引号
        if (x.equalsIgnoreCase("true") || x.equalsIgnoreCase("false")) {
            return x.toUpperCase(Locale.ROOT);
        }

        // 纯数字：不加引号
        if (x.matches("[-+]?\\d+(\\.\\d+)?")) {
            return x;
        }

        // 其它：保留原引号
        return originalQuote + escape(x) + originalQuote;
    }

    private static String escape(String s) {
        return s.replace("'", "''");
    }
}
