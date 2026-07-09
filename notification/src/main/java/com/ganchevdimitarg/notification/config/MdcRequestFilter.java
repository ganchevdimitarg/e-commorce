package com.ganchevdimitarg.notification.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Populates the MDC with {@code traceId} / {@code spanId} / {@code userId} / {@code serviceId}
 * from the gateway-injected headers so every log line is correlatable, then clears it on exit.
 * {@code traceId} and {@code spanId} are parsed out of the W3C {@code traceparent}
 * ({@code version-traceid-spanid-flags}). Header-based (not Tracer-based) to avoid pulling a
 * tracing bridge into this service.
 */
@Component
public class MdcRequestFilter extends OncePerRequestFilter {

    private static final String SERVICE_ID = "notification-service";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String[] parts = headerOrEmpty(request, "traceparent").split("-");
            MDC.put("traceId", parts.length >= 3 ? parts[1] : "");
            MDC.put("spanId", parts.length >= 3 ? parts[2] : "");
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
