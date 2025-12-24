package com.intelligent_data_analysis_system.service;

import com.intelligent_data_analysis_system.infrastructure.datasource.DomainContext;
import com.intelligent_data_analysis_system.infrastructure.datasource.DataSourceDomain;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class SchemaTextProvider {

    private final DataSource dataSource; // 动态数据源最终返回的 DataSource（你现在已能按 domain 切）
    
    // Schema 缓存，key: domain_maxTables_maxColsPerTable
    private final Map<String, String> schemaCache = new ConcurrentHashMap<>();
    
    // 缓存过期时间（毫秒）
    private static final long CACHE_EXPIRY_TIME = 10 * 60 * 1000; // 10分钟
    
    // 缓存条目的过期时间记录
    private final Map<String, Long> cacheExpiryTimes = new ConcurrentHashMap<>();

    /**
     * 生成紧凑 schemaText：TABLE t (col TYPE [PK] [FK -> x.y], ...)
     * @param domain FINANCE / HEALTHCARE
     * @param maxTables 最多输出多少张表（防止太长）
     * @param maxColsPerTable 每表最多多少列（防止太长）
     */
    public String getSchemaText(String domain, int maxTables, int maxColsPerTable) {
        // 生成缓存键
        String cacheKey = generateCacheKey(domain, maxTables, maxColsPerTable);
        
        // 检查缓存是否有效
        if (isCacheValid(cacheKey)) {
            return schemaCache.get(cacheKey);
        }
        
        // 缓存无效，重新生成schema
        String schemaText = generateSchemaText(domain, maxTables, maxColsPerTable);
        
        // 更新缓存
        if (!schemaText.isEmpty()) {
            schemaCache.put(cacheKey, schemaText);
            cacheExpiryTimes.put(cacheKey, System.currentTimeMillis() + CACHE_EXPIRY_TIME);
        }
        
        return schemaText;
    }
    
    /**
     * 生成缓存键
     */
    private String generateCacheKey(String domain, int maxTables, int maxColsPerTable) {
        return domain + "_" + maxTables + "_" + maxColsPerTable;
    }
    
    /**
     * 检查缓存是否有效
     */
    private boolean isCacheValid(String cacheKey) {
        if (!schemaCache.containsKey(cacheKey) || !cacheExpiryTimes.containsKey(cacheKey)) {
            return false;
        }
        
        long expiryTime = cacheExpiryTimes.get(cacheKey);
        return System.currentTimeMillis() < expiryTime;
    }
    
    /**
     * 实际生成schemaText的方法
     */
    private String generateSchemaText(String domain, int maxTables, int maxColsPerTable) {
        DomainContext.set(DataSourceDomain.valueOf(domain));
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData md = conn.getMetaData();

            String catalog = conn.getCatalog();
            String schema = conn.getSchema();

            List<String> tables = new ArrayList<>();
            try (ResultSet rs = md.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String t = rs.getString("TABLE_NAME");
                    // 过滤系统表和日志表
                    if (!t.startsWith("pg_") && !t.endsWith("_log") && !t.endsWith("_logs")) {
                        tables.add(t);
                    }
                }
            }
            Collections.sort(tables);
            if (tables.size() > maxTables) tables = tables.subList(0, maxTables);

            StringBuilder sb = new StringBuilder();
            for (String table : tables) {
                Map<String, String> colType = new LinkedHashMap<>();
                try (ResultSet crs = md.getColumns(catalog, schema, table, "%")) {
                    while (crs.next()) {
                        String c = crs.getString("COLUMN_NAME");
                        String type = crs.getString("TYPE_NAME");
                        colType.put(c, type);
                        if (colType.size() >= maxColsPerTable) break;
                    }
                }

                Set<String> pk = new HashSet<>();
                try (ResultSet prs = md.getPrimaryKeys(catalog, schema, table)) {
                    while (prs.next()) pk.add(prs.getString("COLUMN_NAME"));
                }

                Map<String, String> fk = new HashMap<>(); // col -> refTable.refCol
                try (ResultSet frs = md.getImportedKeys(catalog, schema, table)) {
                    while (frs.next()) {
                        String fkCol = frs.getString("FKCOLUMN_NAME");
                        String pkTable = frs.getString("PKTABLE_NAME");
                        String pkCol = frs.getString("PKCOLUMN_NAME");
                        fk.put(fkCol, pkTable + "." + pkCol);
                    }
                }

                sb.append("TABLE ").append(table).append(" (");
                int i = 0;
                for (Map.Entry<String, String> e : colType.entrySet()) {
                    if (i++ > 0) sb.append(", ");
                    String c = e.getKey();
                    sb.append(c).append(" ").append(e.getValue());
                    if (pk.contains(c)) sb.append(" PK");
                    if (fk.containsKey(c)) sb.append(" FK->").append(fk.get(c));
                }
                sb.append(")\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            // schemaText 获取失败时也别让全流程挂掉
            return "";
        } finally {
            DomainContext.clear();
        }
    }
}
