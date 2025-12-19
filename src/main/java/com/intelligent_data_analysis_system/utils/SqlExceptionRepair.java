package com.intelligent_data_analysis_system.utils;

import com.intelligent_data_analysis_system.mapping.MappingRegistry;

import java.util.Locale;
import java.util.Map;
import java.util.regex.*;

public class SqlExceptionRepair {

    public static String tryRepair(String sql, Exception e) {
        if (sql == null || e == null) return sql;
        String msg = e.getMessage();
        if (msg == null) return sql;

        // 修常见拼写错误
        if (msg.contains("is_acitve")) {
            return sql.replace("is_acitve", "is_active");
        }

        // 可继续补规则
        return sql;
    }

    // 你原来的 tryRepair 保留，这里只给你一个可直接插入的“alias 修复”
    public static String tryAliasRepair(String domain, String sql, Exception e) {
        if (sql == null || sql.isBlank() || e == null) return sql;

        String msg = (e.getMessage() == null ? "" : e.getMessage()).toLowerCase(Locale.ROOT);

        // MySQL / PG 常见报错特征
        boolean tableMissing = msg.contains("doesn't exist")
                || msg.contains("relation") && msg.contains("does not exist")
                || msg.contains("table") && msg.contains("not found");

        if (!tableMissing) return sql;

        String out = sql;
        Map<String, String> aliases = MappingRegistry.get().tableAliases(domain);

        for (var a : aliases.entrySet()) {
            sql = sql.replaceAll("(?i)\\b" + a.getKey() + "\\b", a.getValue());
        }


        return out;
    }
}
