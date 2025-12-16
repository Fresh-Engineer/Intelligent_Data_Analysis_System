package com.intelligent_data_analysis_system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@MapperScan({
        "com.intelligent_data_analysis_system.domain.finance.mapper",
        "com.intelligent_data_analysis_system.domain.healthcare.mapper"
})
public class IntelligentDataAnalysisSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntelligentDataAnalysisSystemApplication.class, args);
    }

}
