package com.intelligent_data_analysis_system.controller;

import com.intelligent_data_analysis_system.infrastructure.datasource.DataSourceDomain;
import com.intelligent_data_analysis_system.infrastructure.datasource.DomainContext;
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
