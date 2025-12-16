package com.intelligent_data_analysis_system.infrastructure.datasource;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class DomainRoutingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String uri = request.getRequestURI();

        try {
            if (uri.startsWith("/api/finance/")) {
                DomainContext.set(DataSourceDomain.FINANCE);
            } else if (uri.startsWith("/api/healthcare/")) {
                DomainContext.set(DataSourceDomain.HEALTHCARE);
            }
            filterChain.doFilter(request, response);
        } finally {
            DomainContext.clear();
        }
    }
}
