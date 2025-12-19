package com.intelligent_data_analysis_system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.intelligent_data_analysis_system.infrastructure.runner.dto.QueryResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DefaultResultNormalizer implements ResultNormalizer {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper mapper;

    public DefaultResultNormalizer() {
        this.mapper = new ObjectMapper();
        this.mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Override
    public String normalize(String problem, String predSql, QueryResult result) {
        if (result == null || !result.isSuccess() || result.isEmpty()) {
            return "[]";
        }

        // 1行1列：直接输出标量（更短、更稳）
        if (result.rowCount() == 1 && result.colCount() == 1) {
            return normalizeValue(result.getFirstValue());
        }

        // 多行多列：输出稳定 JSON（列名固定 + 行顺序固定 + 值归一化）
        List<Map<String, Object>> out = toStableJsonRows(result);
        try {
            return mapper.writeValueAsString(out);
        } catch (Exception e) {
            // 兜底：别让整个流程挂
            return "[]";
        }
    }

    private List<Map<String, Object>> toStableJsonRows(QueryResult qr) {
        List<Map<String, Object>> rows = new ArrayList<>();

        // 列名按出现顺序，同时做一个“稳定排序”的列索引（避免同结果不同列顺序）
        List<String> cols = new ArrayList<>(qr.getColumns());
        List<String> colsSorted = new ArrayList<>(cols);
        colsSorted.sort(String.CASE_INSENSITIVE_ORDER);

        // 行转 map（key 固定为 colsSorted）
        for (List<Object> r : qr.getRows()) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (String c : colsSorted) {
                int idx = indexOfIgnoreCase(cols, c);
                Object v = (idx >= 0 && idx < r.size()) ? r.get(idx) : null;
                m.put(c, normalizeValueRaw(v));
            }
            rows.add(m);
        }

        // 行排序：按整行字符串排序，确保集合相同也能稳定一致
        rows.sort(Comparator.comparing(Map::toString));
        return rows;
    }

    private int indexOfIgnoreCase(List<String> cols, String target) {
        for (int i = 0; i < cols.size(); i++) {
            if (cols.get(i) != null && cols.get(i).equalsIgnoreCase(target)) return i;
        }
        return -1;
    }

    private String normalizeValue(Object v) {
        Object nv = normalizeValueRaw(v);
        return nv == null ? "" : String.valueOf(nv);
    }

    private Object normalizeValueRaw(Object v) {
        if (v == null) return null;

        if (v instanceof BigDecimal bd) {
            return bd.stripTrailingZeros().toPlainString();
        }
        if (v instanceof Integer || v instanceof Long) return v;

        if (v instanceof Number n) {
            // 避免 99.0 这类
            double d = n.doubleValue();
            if (Math.floor(d) == d) return String.valueOf((long) d);
            return String.valueOf(d);
        }

        if (v instanceof java.sql.Date d) return d.toLocalDate().toString();
        if (v instanceof Timestamp ts) return ts.toLocalDateTime().format(DT);

        if (v instanceof LocalDate ld) return ld.toString();
        if (v instanceof LocalDateTime ldt) return ldt.format(DT);
        if (v instanceof OffsetDateTime odt) return odt.toLocalDateTime().format(DT);
        if (v instanceof Instant ins) return DT.format(ins.atZone(ZoneId.systemDefault()).toLocalDateTime());

        return String.valueOf(v).trim();
    }
}
