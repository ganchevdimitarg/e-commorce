package com.ganchevdimitarg.payment.idempotency;

import com.ganchevdimitarg.payment.exception.ConflictException;
import com.ganchevdimitarg.payment.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final Set<String> GUARDED = Set.of("POST", "PUT", "DELETE");
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!GUARDED.contains(request.getMethod())) {
            return true;
        }
        String key = request.getHeader("Idempotency-Key");
        if (key == null || key.isBlank()) {
            throw new ValidationException("Idempotency-Key header is required for write requests");
        }
        Boolean firstSeen = redis.opsForValue()
                .setIfAbsent("payment:idempotency:" + key, "1", TTL);
        if (Boolean.FALSE.equals(firstSeen)) {
            log.warn("Duplicate idempotency key: {}", key);
            throw new ConflictException("Duplicate request for Idempotency-Key: " + key);
        }
        return true;
    }
}
