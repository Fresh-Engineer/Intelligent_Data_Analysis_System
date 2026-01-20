package com.intelligent_data_analysis_system.utils;

import com.intelligent_data_analysis_system.mapping.MappingRegistry;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnumSqlRewriter {

    // gender = 'Female' / "female" / '女' → gender = 'F'
    private static final Pattern GENDER_EQ =
            Pattern.compile("(?i)gender\\s*=\\s*'([^']+)'");

    public static String rewrite(String sql, String domain) {
        if (sql == null || sql.isBlank()) return sql;

        Matcher m = GENDER_EQ.matcher(sql);
        StringBuffer sb = new StringBuffer();

        while (m.find()) {
            String rawValue = m.group(1);

            var mapped = MappingRegistry.get()
                    .mapValue(domain, "patient_master_index.gender", rawValue);

            if (mapped.isPresent()) {
                m.appendReplacement(sb,
                        "gender = '" + mapped.get() + "'");
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
