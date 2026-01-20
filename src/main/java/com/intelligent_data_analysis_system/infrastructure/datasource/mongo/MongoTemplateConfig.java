package com.intelligent_data_analysis_system.infrastructure.datasource.mongo;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
public class MongoTemplateConfig {

    @Primary
    @Bean("financeMongoTemplate")
    public MongoTemplate financeMongoTemplate(
            @Value("${spring.datasource.finance_mongoDB.url}")
            String url,
            @Value("${spring.datasource.finance_mongoDB.username}")
            String username,
            @Value("${spring.datasource.finance_mongoDB.password}")
            String password
    ) {
        return buildMongoTemplate(url, username, password);
    }

    @Bean("healthcareMongoTemplate")
    public MongoTemplate healthcareMongoTemplate(
            @Value("${spring.datasource.healthcare_mongoDB.url}")
            String url,
            @Value("${spring.datasource.healthcare_mongoDB.username}")
            String username,
            @Value("${spring.datasource.healthcare_mongoDB.password}")
            String password
    ) {
        return buildMongoTemplate(url, username, password);
    }

    private MongoTemplate buildMongoTemplate(String url, String username, String password) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("MongoDB url 未配置");
        }

        String finalUrl = injectCredential(url, username, password);

        ConnectionString cs = new ConnectionString(finalUrl);
        if (cs.getDatabase() == null) {
            throw new IllegalStateException(
                    "MongoDB url 必须包含数据库名，例如 mongodb://host:27017/db?authSource=admin"
            );
        }

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(cs)
                .build();

        MongoClient client = MongoClients.create(settings);
        return new MongoTemplate(client, cs.getDatabase());
    }

    private String injectCredential(String url, String username, String password) {
        if (username == null || username.isBlank()) {
            return url; // 允许无认证
        }

        if (url.contains("@")) {
            return url; // 已包含凭证
        }

        String u = URLEncoder.encode(username, StandardCharsets.UTF_8);
        String p = URLEncoder.encode(password == null ? "" : password, StandardCharsets.UTF_8);

        if (url.startsWith("mongodb://")) {
            return "mongodb://" + u + ":" + p + "@" + url.substring("mongodb://".length());
        }
        if (url.startsWith("mongodb+srv://")) {
            return "mongodb+srv://" + u + ":" + p + "@" + url.substring("mongodb+srv://".length());
        }
        return url;
    }
}
