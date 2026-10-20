package com.ganchevdimitarg.catalog.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * Captures the response body of guarded requests so {@link IdempotencyInterceptor} can store it
 * for replay.
 *
 * <p>Ordered at {@link Ordered#LOWEST_PRECEDENCE} so it runs after Spring Security and wraps the
 * {@code DispatcherServlet}: the interceptor can then read the committed body from the wrapper in
 * {@code afterCompletion}. {@link ContentCachingResponseWrapper#copyBodyToResponse()} in a
 * {@code finally} block keeps ordinary (non-replayed) responses intact.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class IdempotencyResponseCaptureFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!IdempotencyPolicy.isGuarded(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(request, wrapper);
        } finally {
            wrapper.copyBodyToResponse();
        }
    }
}
