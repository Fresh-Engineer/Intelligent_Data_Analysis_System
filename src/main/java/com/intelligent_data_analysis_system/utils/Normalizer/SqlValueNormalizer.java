package com.intelligent_data_analysis_system.utils.Normalizer;

public class SqlValueNormalizer {

    /**
     * 把常见的 “Y/N/1/0/true/false” 统一为数据库标准 boolean
     * 只在 WHERE 条件中做简单替换，避免误伤
     */
    public static String normalize(String sql) {
        if (sql == null || sql.isBlank()) return sql;

        String s = sql;

        // 统一大小写处理（只针对值）
        s = s.replaceAll("(?i)=\\s*'Y'", "= TRUE");
        s = s.replaceAll("(?i)=\\s*'N'", "= FALSE");

        s = s.replaceAll("(?i)=\\s*'1'", "= TRUE");
        s = s.replaceAll("(?i)=\\s*'0'", "= FALSE");

        s = s.replaceAll("(?i)=\\s*1\\b", "= TRUE");
        s = s.replaceAll("(?i)=\\s*0\\b", "= FALSE");

        s = s.replaceAll("(?i)=\\s*true\\b", "= TRUE");
        s = s.replaceAll("(?i)=\\s*false\\b", "= FALSE");

        return s;
    }
}
