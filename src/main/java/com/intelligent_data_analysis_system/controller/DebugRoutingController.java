package com.intelligent_data_analysis_system.controller;

import com.intelligent_data_analysis_system.infrastructure.datasource.DataSourceDomain;
import com.intelligent_data_analysis_system.infrastructure.datasource.DomainContext;
import com.intelligent_data_analysis_system.service.SqlExecuteService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.util.Map;

@RestController
@RequestMapping("/api/debug")
@RequiredArgsConstructor
public class DebugRoutingController {

    private final DataSource dataSource;

    private final SqlExecuteService sqlExecuteService;

    /**
     * 统一 SQL 执行入口：
     * - 支持 MySQL / PostgreSQL 多数据源动态路由
     * - 供 text2sql / Agent 生成 SQL 后统一调用
     * - 内置只读校验与行数限制，避免大模型误生成破坏性查询
     */
    @PostMapping("/execute")
    public Map<String, Object> execute(@RequestBody Map<String, Object> body) {
        return sqlExecuteService.execute(body);
    }

    @GetMapping("/ping/{domain}")
    public Map<String, Object> ping(@PathVariable String domain) {
        DataSourceDomain d = "healthcare".equalsIgnoreCase(domain)
                ? DataSourceDomain.HEALTHCARE
                : DataSourceDomain.FINANCE;

        try {
            DomainContext.set(d);
            Integer one = new JdbcTemplate(dataSource).queryForObject("select 1", Integer.class);
            return Map.of("domain", d.name(), "select1", one);
        } finally {
            DomainContext.clear();
        }
    }
}
