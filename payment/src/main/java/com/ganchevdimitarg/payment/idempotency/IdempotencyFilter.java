package com.ganchevdimitarg.payment.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

/**
 * Idempotency guard for mutating payment endpoints. Implements cache-aside <em>replay</em>
 * (root {@code idempotency.md}) rather than mere duplicate rejection:
 * <ul>
 *   <li>On the first request for a key, a short-lived lock is taken, the request is
 *       processed, and — only on a 2xx — the full response (status, content type, body) is
 *       stored under a 24h TTL.</li>
 *   <li>A subsequent request with the same key replays the stored response verbatim.</li>
 *   <li>If processing fails (non-2xx), the lock is released so the key is immediately
 *       retriable — a transient downstream failure never poisons the key for 24h.</li>
 *   <li>A concurrent in-flight duplicate (lock held, no stored response yet) gets a 409.</li>
 * </ul>
 * An absent {@code Idempotency-Key} is honoured (the request proceeds), relying on the
 * provider-level idempotency key passed through to Stripe.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Set<String> GUARDED = Set.of("POST", "PUT", "DELETE");
    private static final Duration RESPONSE_TTL = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final String KEY_PREFIX = "payment:idempotency:";
    private static final String HEADER = "Idempotency-Key";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /** Stored form of a completed response, replayed on a duplicate key. */
    record StoredResponse(int status, String contentType, String bodyBase64) {
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/payment/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (!GUARDED.contains(request.getMethod()) || key == null || key.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        String responseKey = KEY_PREFIX + key;
        String lockKey = responseKey + ":lock";

        String cached = redis.opsForValue().get(responseKey);
        if (cached != null) {
            log.info("Replaying stored response for Idempotency-Key");
            replay(response, cached);
            return;
        }

        Boolean acquired = redis.opsForValue().setIfAbsent(lockKey, "1", LOCK_TTL);
        if (Boolean.FALSE.equals(acquired)) {
            log.warn("Concurrent in-flight request for Idempotency-Key");
            writeProblem(response, HttpStatus.CONFLICT, "IN_PROGRESS",
                    "A request with this Idempotency-Key is already in progress");
            return;
        }

        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        boolean stored = false;
        try {
            filterChain.doFilter(request, wrapper);
            int status = wrapper.getStatus();
            if (status >= 200 && status < 300) {
                store(responseKey, wrapper);
                stored = true;
            }
        } finally {
            wrapper.copyBodyToResponse();
            // A failed request must stay retriable with the same key — never poisoned for 24h.
            if (!stored) {
                redis.delete(lockKey);
            }
        }
    }

    private void store(String responseKey, ContentCachingResponseWrapper wrapper) {
        try {
            StoredResponse stored = new StoredResponse(
                    wrapper.getStatus(),
                    wrapper.getContentType(),
                    Base64.getEncoder().encodeToString(wrapper.getContentAsByteArray()));
            redis.opsForValue().set(responseKey, objectMapper.writeValueAsString(stored), RESPONSE_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Could not store idempotent response for replay: {}", e.getMessage());
        }
    }

    private void replay(HttpServletResponse response, String cached) throws IOException {
        StoredResponse stored = objectMapper.readValue(cached, StoredResponse.class);
        response.setStatus(stored.status());
        if (stored.contentType() != null) {
            response.setContentType(stored.contentType());
        }
        response.getOutputStream().write(Base64.getDecoder().decode(stored.bodyBase64()));
        response.getOutputStream().flush();
    }

    private void writeProblem(HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
