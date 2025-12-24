package com.intelligent_data_analysis_system.infrastructure.config.properties;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "spring.datasource")
public class MultiDataSourceProperties {

    private DbProps financeMysql;
    private DbProps financePgsql;
    private DbProps financeMongoDB;
    private DbProps healthcareMysql;
    private DbProps healthcarePgsql;
    private DbProps healthcareMongoDB;


    @Data
    public static class DbProps {
        private String url;
        private String username;
        private String password;
        private String driverClassName;
    }

    @PostConstruct
    public void check() {
        System.out.println("financePg=" + financePgsql);
        System.out.println("healthcarePg=" + healthcarePgsql);
        System.out.println("financeMysql=" + financeMysql);
        System.out.println("healthcareMysql=" + healthcareMysql);
        System.out.println("financeMongoDB=" + financeMongoDB);
        System.out.println("healthcareMongoDB=" + healthcareMongoDB);
    }

}
