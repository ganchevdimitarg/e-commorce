package com.ganchevdimitarg.auth.idempotency;

import com.ganchevdimitarg.auth.exception.ConflictException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Set;

/**
 * Honors an optional {@code Idempotency-Key} header on mutating requests. Unlike the canonical
 * {@code payment}/{@code catalog} implementation, the key is NOT required here: the public
 * {@code POST /register} entry point must stay non-breaking for callers that omit it. When a
 * key is present and has already been seen within the TTL window, the duplicate request is
 * rejected with {@link ConflictException} (409); a fresh key or the absence of a key proceeds.
 */
@Slf4j
@RequiredArgsConstructor
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final Set<String> GUARDED = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Duration TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "auth:idempotency:";

    private final StringRedisTemplate redis;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!GUARDED.contains(request.getMethod())) {
            return true;
        }
        String key = request.getHeader("Idempotency-Key");
        if (key == null || key.isBlank()) {
            log.debug("No Idempotency-Key header present; proceeding without dedupe");
            return true;
        }
        Boolean firstSeen = redis.opsForValue().setIfAbsent(KEY_PREFIX + key, "1", TTL);
        if (Boolean.FALSE.equals(firstSeen)) {
            log.debug("Duplicate idempotency key: {}", key);
            throw new ConflictException("Duplicate request for Idempotency-Key: " + key);
        }
        return true;
    }
}
