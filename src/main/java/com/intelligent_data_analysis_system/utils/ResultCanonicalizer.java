package com.intelligent_data_analysis_system.utils;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ResultCanonicalizer {

    private static final DateTimeFormatter DT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String canonicalize(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return "[]";

        // 1) 统一列名集合
        Set<String> colSet = new TreeSet<>();
        for (Map<String, Object> r : rows) colSet.addAll(r.keySet());
        List<String> cols = new ArrayList<>(colSet); // 已排序

        // 2) 每行变成稳定字符串：按列顺序拼接
        List<String> normLines = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            StringBuilder sb = new StringBuilder();
            for (String c : cols) {
                sb.append(c).append('=').append(normValue(r.get(c))).append('|');
            }
            normLines.add(sb.toString());
        }

        // 3) 行排序，避免无 order by 造成顺序不同
        Collections.sort(normLines);

        // 4) 输出稳定表示
        return normLines.toString();
    }

    private static String normValue(Object v) {
        if (v == null) return "NULL";

        if (v instanceof BigDecimal bd) {
            // 统一小数位（你可调整）
            return bd.stripTrailingZeros().toPlainString();
        }
        if (v instanceof Number n) {
            return String.valueOf(n);
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate().toString();
        }
        if (v instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().format(DT);
        }
        if (v instanceof LocalDate ld) return ld.toString();
        if (v instanceof LocalDateTime ldt) return ldt.format(DT);
        if (v instanceof OffsetDateTime odt) return odt.toLocalDateTime().format(DT);
        if (v instanceof Instant ins) return DT.format(ins.atZone(ZoneId.systemDefault()).toLocalDateTime());

        // 统一去掉多余空白
        return String.valueOf(v).trim();
    }
}
