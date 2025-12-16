package com.intelligent_data_analysis_system.infrastructure.config.properties;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.routing")
public class RoutingProperties {
    /**
     * mysql / pg
     */
    private String dbms;
}
