package com.concordeu.catalog.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class MdcRequestFilter extends OncePerRequestFilter {

    private static final String SERVICE_ID = "catalog-service";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String traceparent = request.getHeader("traceparent");
            if (traceparent != null) {
                MDC.put("traceId", traceparent);
            }
            MDC.put("userId", headerOrEmpty(request, "X-User-Id"));
            MDC.put("serviceId", SERVICE_ID);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String headerOrEmpty(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value != null ? value : "";
    }
}
