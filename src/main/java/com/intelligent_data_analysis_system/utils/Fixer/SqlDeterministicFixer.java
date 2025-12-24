package com.intelligent_data_analysis_system.utils.Fixer;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SqlDeterministicFixer {
    private String repairSqlForExecution(String sql, String errorMessage, String domain, String dbms) {
        if (sql == null) return sql;
        String s = sql.trim();
        if (errorMessage == null) return s;

        String err = errorMessage.toLowerCase(Locale.ROOT);

        // 1) QUALIFY（你日志里 6037）
        if (err.contains("qualify")) {
            s = rewriteQualify(s);
        }

        // 2) 明确的 ambiguous 列（从错误里抠出 Column 'xxx'）
        // MySQL: Column 'client_id' in field list is ambiguous
        // PG: column reference "client_id" is ambiguous
        String ambCol = extractAmbiguousColumn(errorMessage);
        if (ambCol != null) {
            s = prefixBareColumnWithFirstAlias(s, ambCol);
        }

        // 3) managers 错把 client_name/create_time 放在 managers（你日志里高发）
        if (s.toLowerCase(Locale.ROOT).contains(" from managers")
                && (containsBareWord(s, "client_name") || containsBareWord(s, "create_time"))) {
            s = ensureJoinClientsForManagers(s);
            s = s.replaceAll("(?i)(?<!\\.)\\bclient_name\\b", "c.client_name");
            s = s.replaceAll("(?i)(?<!\\.)\\bcreate_time\\b", "c.create_time");
        }

        // 4) 医疗库字段：created_time（你日志里写错 create_time）
        if ("healthcare".equalsIgnoreCase(domain) || domain.toUpperCase().contains("HEALTHCARE")) {
            s = s.replaceAll("(?i)(?<!\\.)\\bcreate_time\\b", "created_time");
        }

        // 5) counterparties: inception_date -> establish_date
        s = s.replaceAll("(?i)\\binception_date\\b", "establish_date");

        return s;
    }

    private String rewriteQualify(String sql) {
        // 把 "... QUALIFY condition" 改成 "SELECT * FROM ( ... ) x WHERE condition"
        String lower = sql.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(" qualify ");
        if (idx < 0) return sql;

        String inner = sql.substring(0, idx).trim();
        String cond = sql.substring(idx + " qualify ".length()).trim();

        inner = trimTrailingSemicolon(inner);
        cond = trimTrailingSemicolon(cond);

        return "SELECT * FROM (\n" + inner + "\n) x\nWHERE " + cond;
    }

    private String extractAmbiguousColumn(String errorMessage) {
        // MySQL pattern: Column 'client_id' ... ambiguous
        Matcher m1 = Pattern.compile("Column '([^']+)' .* ambiguous", Pattern.CASE_INSENSITIVE).matcher(errorMessage);
        if (m1.find()) return m1.group(1);

        // Postgres pattern: column reference "client_id" is ambiguous
        Matcher m2 = Pattern.compile("column reference \"([^\"]+)\" is ambiguous", Pattern.CASE_INSENSITIVE).matcher(errorMessage);
        if (m2.find()) return m2.group(1);

        return null;
    }

    private String prefixBareColumnWithFirstAlias(String sql, String col) {
        // 找第一个 FROM 表别名：FROM table alias
        Matcher m = Pattern.compile("(?i)\\bfrom\\s+([a-zA-Z_][\\w]*)\\s+([a-zA-Z_][\\w]*)").matcher(sql);
        String alias = null;
        if (m.find()) alias = m.group(2);
        if (alias == null) {
            // 没写别名的话就不动（但你 prompt 已要求必须写别名）
            return sql;
        }
        // 只替换裸列（前面不是 '.'）
        return sql.replaceAll("(?i)(?<!\\.)\\b" + Pattern.quote(col) + "\\b", alias + "." + col);
    }

    private boolean containsBareWord(String sql, String word) {
        return Pattern.compile("(?i)(?<!\\.)\\b" + Pattern.quote(word) + "\\b").matcher(sql).find();
    }

    private String ensureJoinClientsForManagers(String sql) {
        if (Pattern.compile("(?i)\\bjoin\\s+clients\\b").matcher(sql).find()) return sql;

        // 找 managers 别名：FROM managers m
        Matcher fm = Pattern.compile("(?i)\\bfrom\\s+managers\\s+([a-zA-Z_][\\w]*)").matcher(sql);
        String mAlias = fm.find() ? fm.group(1) : "m";

        String join = " JOIN clients c ON c.manager_id = " + mAlias + ".manager_id ";

        // 插到第一个 WHERE 前
        Matcher wm = Pattern.compile("(?i)\\bwhere\\b").matcher(sql);
        if (wm.find()) {
            int idx = wm.start();
            return sql.substring(0, idx) + join + sql.substring(idx);
        }
        return sql + join;
    }

    private String trimTrailingSemicolon(String s) {
        s = s.trim();
        if (s.endsWith(";")) return s.substring(0, s.length() - 1).trim();
        return s;
    }

}
