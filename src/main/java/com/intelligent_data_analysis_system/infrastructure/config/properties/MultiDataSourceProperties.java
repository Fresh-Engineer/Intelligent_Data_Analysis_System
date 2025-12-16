package com.intelligent_data_analysis_system.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "spring.datasource")
public class MultiDataSourceProperties {

    private DbProps financeMysql;
    private DbProps financePg;
    private DbProps healthcareMysql;
    private DbProps healthcarePg;

    @Data
    public static class DbProps {
        private String url;
        private String username;
        private String password;
        private String driverClassName;
    }
}
