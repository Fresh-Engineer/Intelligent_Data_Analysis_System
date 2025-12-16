package com.intelligent_data_analysis_system.infrastructure.diagnostics;

import com.intelligent_data_analysis_system.infrastructure.config.properties.RoutingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@RequiredArgsConstructor
public class BootVerify {

    private final RoutingProperties routingProperties;
    private final DataSource dataSource;

    @Bean
    public ApplicationRunner verifyRouting() {
        return args -> {
            System.out.println("===== Routing Verify =====");
            System.out.println("dbms = " + routingProperties.getDbms());
            System.out.println("DataSource bean = " + dataSource.getClass().getName());
            System.out.println("lookup keys: FINANCE / HEALTHCARE");
            System.out.println("==========================");
        };
    }
}
