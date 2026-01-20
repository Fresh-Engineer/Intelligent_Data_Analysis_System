package com.intelligent_data_analysis_system.mapping;

import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

public class MappingRegistry {

    private static final MappingRegistry INSTANCE = new MappingRegistry();

    private final Map<String, Object> root;

    private MappingRegistry() {
        try {
            Yaml yaml = new Yaml();
            InputStream in = new ClassPathResource("mapping.yml").getInputStream();
            this.root = yaml.load(in);
        } catch (Exception e) {
            throw new RuntimeException("加载 mapping.yml 失败", e);
        }
    }

    public static MappingRegistry get() {
        return INSTANCE;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> domain(String domain) {
        Object d = root.get(domain.toLowerCase(Locale.ROOT));
        return d instanceof Map ? (Map<String, Object>) d : Map.of();
    }

    /* =========================
       A. 枚举值映射
       ========================= */
    @SuppressWarnings("unchecked")
    public Optional<String> mapValue(String domain, String fullColumn, String input) {
        if (input == null) return Optional.empty();

        Map<String, Object> dm = domain(domain);
        Object vm = dm.get("value_mapping");
        if (!(vm instanceof Map)) return Optional.empty();

        Map<String, Object> valueMap = (Map<String, Object>) vm;

        String colKey = normalizeFullColumn(fullColumn);
        Object colMap = valueMap.get(colKey);
        if (!(colMap instanceof Map)) return Optional.empty();

        Map<String, Object> m = (Map<String, Object>) colMap;

        String key = input.trim();
        Object v = m.get(key);
        if (v == null) v = m.get(key.toLowerCase(Locale.ROOT));
        if (v == null) v = m.get(key.toUpperCase(Locale.ROOT));
        if (v == null) return Optional.empty();

        return Optional.of(String.valueOf(v));
    }

    private String normalizeFullColumn(String fullColumn) {
        if (fullColumn == null) return "";
        String s = fullColumn.trim();
        // public.clients.status -> clients.status
        if (countDots(s) >= 2) {
            s = s.substring(s.indexOf('.') + 1);
        }
        return s.toLowerCase(Locale.ROOT);
    }

    private int countDots(String s) {
        int c = 0;
        for (char ch : s.toCharArray()) if (ch == '.') c++;
        return c;
    }


    /* =========================
       B. allowed_values（枚举/范围）
       ========================= */
    @SuppressWarnings("unchecked")
    public Optional<Object> allowedValues(String domain, String fullColumn) {
        Map<String, Object> dm = domain(domain);
        Object av = dm.get("allowed_values");
        if (!(av instanceof Map)) return Optional.empty();

        return Optional.ofNullable(((Map<String, Object>) av).get(fullColumn));
    }

    /* =========================
       C. 表别名
       ========================= */
    @SuppressWarnings("unchecked")
    public Map<String, String> tableAliases(String domain) {
        Map<String, Object> dm = domain(domain);
        Object ta = dm.get("table_alias");
        if (!(ta instanceof Map)) return Map.of();

        Map<String, String> res = new HashMap<>();
        ((Map<String, Object>) ta).forEach((k, v) -> res.put(k, String.valueOf(v)));
        return res;
    }

    @SuppressWarnings("unchecked")
    public String buildEnumConstraintPrompt(String domain) {
        Map<String, Object> dm = domain(domain);
        Object vm = dm.get("value_mapping");
        if (!(vm instanceof Map)) return "";

        Map<String, Object> valueMap = (Map<String, Object>) vm;

        StringBuilder sb = new StringBuilder();
        sb.append("【枚举值强约束（必须严格遵守）】\n");

        valueMap.forEach((col, mappingObj) -> {
            if (!(mappingObj instanceof Map)) return;

            Map<?, ?> m = (Map<?, ?>) mappingObj;

            // 允许值集合（把各种类型都转成字符串）
            Set<String> uniq = new LinkedHashSet<>();
            for (Object v : m.values()) {
                if (v == null) continue;
                uniq.add(String.valueOf(v));
            }

            sb.append("- ").append(String.valueOf(col))
                    .append(" 只允许使用以下编码值：")
                    .append(String.join(", ", uniq))
                    .append("\n");

            sb.append("  语义映射规则：\n");
            m.forEach((k, v) -> {
                sb.append("  - ").append(String.valueOf(k))
                        .append(" → '").append(String.valueOf(v)).append("'\n");
            });
        });

        sb.append("- 禁止在 SQL 中直接使用自然语言值（如 Female / Male / 女 / 男）\n");
        sb.append("- 禁止在 SQL 中直接使用英汉翻译值（如 Cancelled）\n");
        return sb.toString();
    }


}
