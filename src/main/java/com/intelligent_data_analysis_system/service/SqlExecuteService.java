package com.intelligent_data_analysis_system.service;

import com.intelligent_data_analysis_system.infrastructure.datasource.DomainContext;
import com.intelligent_data_analysis_system.infrastructure.datasource.DataSourceDomain;
import com.intelligent_data_analysis_system.utils.SqlGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SqlExecuteService {

    private final NamedParameterJdbcTemplate namedJdbc;

    /**
     * 你仓库里已经有 app.routing.dbms（mysql / pgsql），这里直接复用。
     * 没配就默认 mysql。
     */
    @Value("${app.routing.dbms:mysql}")
    private String defaultDbms;

    public SqlExecuteService(NamedParameterJdbcTemplate namedJdbc) {
        this.namedJdbc = namedJdbc;
    }

    /**
     * 统一执行入口（给 controller / agent 用）
     * body 支持字段：
     * - domain: "finance" / "healthcare" 或者直接传 enum 名（FINANCE_MYSQL 等）
     * - dbms: "mysql" / "pgsql"（可选；不传就用 app.routing.dbms）
     * - sql:  要执行的 SQL
     * - params: 命名参数（可选）
     * - maxRows: 行数上限（可选，默认 200）
     */
    public Map<String, Object> execute(Map<String, Object> body) {
        if (body == null) body = Collections.emptyMap();

        String sql = asString(body.get("sql"));
        String domain = asString(body.get("domain"));
        String dbms = asString(body.getOrDefault("dbms", defaultDbms));
        int maxRows = asInt(body.get("maxRows"), 200);

        SqlGuard.validateReadOnlySingleStatement(sql);
        sql = SqlGuard.ensureLimit(sql, maxRows);

        DataSourceDomain dsDomain = resolveDomain(domain, dbms);

        long t0 = System.currentTimeMillis();
        DomainContext.set(dsDomain);
        try {
            Map<String, Object> params = asMap(body.get("params"));

            List<Map<String, Object>> rows =
                    namedJdbc.query(sql, params, new ColumnMapRowMapper());

            List<String> columns = rows.isEmpty()
                    ? List.of()
                    : new ArrayList<>(rows.get(0).keySet());

            long elapsed = System.currentTimeMillis() - t0;

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("dataSource", dsDomain.name());
            resp.put("elapsedMs", elapsed);
            resp.put("columns", columns);
            resp.put("rows", rows);
            resp.put("rowCount", rows.size());
            return resp;
        } finally {
            DomainContext.clear();
        }
    }

    /**
     * 把 (finance/healthcare + mysql/pgsql) 解析成你已有的 DataSourceDomain 枚举。
     * 兼容两种输入：
     * 1) domain 直接传 enum 名：FINANCE_MYSQL / HEALTHCARE_PGSQL ...
     * 2) domain 传 finance/healthcare，dbms 传 mysql/pgsql，然后拼成 FINANCE_MYSQL ...
     */
    private DataSourceDomain resolveDomain(String domain, String dbms) {
        if (domain == null || domain.isBlank()) {
            throw new IllegalArgumentException("domain 不能为空（finance/healthcare 或 DataSourceDomain 枚举名）");
        }

        String d = domain.trim().toUpperCase(Locale.ROOT);
        String m = (dbms == null ? "MYSQL" : dbms.trim().toUpperCase(Locale.ROOT));

        // 常见写法：pgsql / postgres / postgresql 都归一到 PGSQL
        if (m.equals("POSTGRES") || m.equals("POSTGRESQL") || m.equals("PG")) {
            m = "PGSQL";
        }

        // 1) 先尝试直接当 enum
        try {
            return DataSourceDomain.valueOf(d);
        } catch (Exception ignore) {}

        // 2) 再按 finance/healthcare + dbms 拼 enum
        // 例如 FINANCE + MYSQL => FINANCE_MYSQL
        String candidate = d + "_" + m;
        try {
            return DataSourceDomain.valueOf(candidate);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "无法解析数据源 domain=" + domain + ", dbms=" + dbms +
                            "，请确认 DataSourceDomain 是否包含：" + candidate
            );
        }
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static int asInt(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); }
        catch (Exception e) { return def; }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        if (o == null) return Collections.emptyMap();
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> r = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                r.put(String.valueOf(e.getKey()), e.getValue());
            }
            return r;
        }
        throw new IllegalArgumentException("params 必须是 JSON object / Map");
    }
}
