package com.ganchevdimitarg.catalog.ratelimit;

import com.ganchevdimitarg.catalog.exception.RateLimitExceededException;
import com.ganchevdimitarg.catalog.idempotency.IdempotencyKeys;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces {@link RateLimited} on the resolved handler method.
 *
 * <p>The allowance is keyed by the JWT principal, so one noisy client cannot consume another
 * client's budget. The rejection counter carries only the bucket as a tag: a per-principal tag
 * would grow without bound.
 */
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    static final String REJECTED_METRIC = "catalog.rate_limit.rejected";

    private final RateLimiterCache limiterCache;
    private final RateLimitProperties properties;
    private final MeterRegistry meterRegistry;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.enabled() || !(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimited rateLimited = handlerMethod.getMethodAnnotation(RateLimited.class);
        if (rateLimited == null) {
            return true;
        }
        RateLimitBucket bucket = rateLimited.value();
        RateLimiter limiter = limiterCache.get(bucket, IdempotencyKeys.principalName());
        // A zero permission timeout makes acquirePermission non-blocking: it returns false
        // immediately rather than parking the request thread until the next refresh.
        if (limiter.acquirePermission()) {
            return true;
        }
        meterRegistry.counter(REJECTED_METRIC, "bucket", bucket.label()).increment();
        throw new RateLimitExceededException(bucket.label(), retryAfterSeconds(bucket));
    }

    /**
     * Resilience4j refreshes permits once per period, so waiting one full period guarantees a
     * fresh allowance. A sub-second period rounds up because {@code Retry-After} is expressed in
     * whole seconds.
     */
    private long retryAfterSeconds(RateLimitBucket bucket) {
        return Math.max(1, properties.limitFor(bucket).period().toSeconds());
    }
}
