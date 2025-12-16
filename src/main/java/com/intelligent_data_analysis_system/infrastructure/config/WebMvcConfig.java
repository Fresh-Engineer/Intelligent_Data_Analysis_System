package com.intelligent_data_analysis_system.infrastructure.config;

import com.intelligent_data_analysis_system.infrastructure.datasource.DomainRoutingInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new DomainRoutingInterceptor())
                .addPathPatterns("/api/**"); // 只拦你的 API
    }
}
