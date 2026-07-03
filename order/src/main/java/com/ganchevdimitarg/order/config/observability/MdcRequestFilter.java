package com.ganchevdimitarg.order.config.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Populates the MDC with {@code traceId} / {@code userId} / {@code serviceId} from the
 * gateway-injected headers so every log line is correlatable, then clears it on exit.
 * Header-based (not Tracer-based) to avoid pulling a tracing bridge into this service.
 */
@Component
public class MdcRequestFilter extends OncePerRequestFilter {

    private static final String SERVICE_ID = "order-service";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            MDC.put("traceId", headerOrEmpty(request, "traceparent"));
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
