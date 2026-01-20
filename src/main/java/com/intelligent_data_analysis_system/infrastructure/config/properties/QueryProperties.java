package com.intelligent_data_analysis_system.infrastructure.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.query")
public class QueryProperties {
    /**
     * Maximum number of rows to return in query results
     */
    private int maxRows = 200;
}