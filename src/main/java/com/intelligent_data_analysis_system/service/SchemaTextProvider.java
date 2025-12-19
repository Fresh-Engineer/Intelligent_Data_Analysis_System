package com.intelligent_data_analysis_system.service;

import com.intelligent_data_analysis_system.infrastructure.datasource.DomainContext;
import com.intelligent_data_analysis_system.infrastructure.datasource.DataSourceDomain;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SchemaTextProvider {

    private final DataSource dataSource; // 动态数据源最终返回的 DataSource（你现在已能按 domain 切）

    /**
     * 生成紧凑 schemaText：TABLE t (col TYPE [PK] [FK -> x.y], ...)
     * @param domain FINANCE / HEALTHCARE
     * @param maxTables 最多输出多少张表（防止太长）
     * @param maxColsPerTable 每表最多多少列（防止太长）
     */
    public String getSchemaText(String domain, int maxTables, int maxColsPerTable) {
        DomainContext.set(DataSourceDomain.valueOf(domain));
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData md = conn.getMetaData();

            String catalog = conn.getCatalog();
            String schema = conn.getSchema();

            List<String> tables = new ArrayList<>();
            try (ResultSet rs = md.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String t = rs.getString("TABLE_NAME");
                    // 你也可以在这里过滤系统表/日志表等
                    tables.add(t);
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
