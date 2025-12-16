package com.intelligent_data_analysis_system.infrastructure.datasource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

public class DomainRoutingInterceptor implements HandlerInterceptor {

    // 方案1：按 Header（可选）
    private static final String HEADER_DOMAIN = "X-Domain";

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler
    ) {
        // 方案2：按 URL 前缀（推荐先用这个）
        // 例如：
        // /api/healthcare/** -> HEALTHCARE
        // /api/finance/**    -> FINANCE
        String uri = request.getRequestURI();

        DataSourceDomain domain = null;

        if (uri.startsWith("/api/healthcare/")) {
            domain = DataSourceDomain.HEALTHCARE;
        } else if (uri.startsWith("/api/finance/")) {
            domain = DataSourceDomain.FINANCE;
        } else {
            // 如果你希望“默认域”，就放开这一行（比如默认 FINANCE）
            // domain = DataSourceDomain.FINANCE;

            // 否则不设置，让 routingDataSource 走默认（你自己配置的 defaultTargetDataSource）
            return true;
        }

        DomainContext.set(domain);
        return true;
    }

    @Override
    public void afterCompletion(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler,
            Exception ex
    ) {
        // 必须清理线程变量，避免线程复用导致串库
        DomainContext.clear();
    }
}
