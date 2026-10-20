package com.ganchevdimitarg.catalog.idempotency;

import com.ganchevdimitarg.catalog.exception.ConflictException;
import com.ganchevdimitarg.catalog.exception.ValidationException;
import com.ganchevdimitarg.catalog.idempotency.IdempotencyStore.ClaimResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Claims an idempotency key before a guarded request executes and records the outcome after it
 * finishes.
 *
 * <ul>
 *   <li>New key: claim it as IN_PROGRESS and continue.</li>
 *   <li>COMPLETED key: replay the stored status, content type, {@code Location} and body without
 *       invoking the handler.</li>
 *   <li>IN_PROGRESS key: 409 — a concurrent duplicate must not execute.</li>
 *   <li>Redis unavailable: fail open per ADR-006; the request proceeds unprotected.</li>
 * </ul>
 *
 * <p>After completion a 5xx or an escaping exception releases the key so a retry can execute;
 * every other outcome stores a COMPLETED record for replay.
 */
@Slf4j
public class IdempotencyInterceptor implements HandlerInterceptor {

    static final String REDIS_KEY_ATTRIBUTE = IdempotencyInterceptor.class.getName() + ".key";

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final IdempotencyStore store;

    public IdempotencyInterceptor(IdempotencyStore store) {
        this.store = store;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!IdempotencyPolicy.isGuarded(request)) {
            return true;
        }
        String clientKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (clientKey == null || clientKey.isBlank()) {
            throw new ValidationException("Idempotency-Key header is required for write requests");
        }
        String redisKey = IdempotencyKeys.scopedKey(
                IdempotencyKeys.principalName(), request.getMethod(), request.getRequestURI(), clientKey);
        ClaimResult result;
        try {
            result = store.claim(redisKey);
        } catch (IdempotencyUnavailableException e) {
            log.warn("Idempotency store unavailable; proceeding without protection: {}", e.getMessage());
            return true;                                 // ADR-006: fail-open
        }
        return switch (result) {
            case ClaimResult.Claimed c -> {
                request.setAttribute(REDIS_KEY_ATTRIBUTE, redisKey);
                yield true;
            }
            case ClaimResult.Completed c -> {
                replay(c.record(), response);
                yield false;
            }
            case ClaimResult.InProgress i ->
                    throw new ConflictException("A request with this Idempotency-Key is in progress");
        };
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Object attribute = request.getAttribute(REDIS_KEY_ATTRIBUTE);
        if (attribute == null) {
            return;                                      // not first-seen, replayed, or fail-open
        }
        String redisKey = (String) attribute;
        if (ex != null || response.getStatus() >= 500) {
            safeRelease(redisKey);
            return;
        }
        String body = response instanceof ContentCachingResponseWrapper wrapper
                ? new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8) : null;
        safeComplete(redisKey, IdempotencyRecord.completed(
                response.getStatus(), response.getContentType(),
                response.getHeader(HttpHeaders.LOCATION), body));
    }

    private void replay(IdempotencyRecord record, HttpServletResponse response) throws IOException {
        response.setStatus(record.status());
        if (record.contentType() != null) {
            response.setContentType(record.contentType());
        }
        if (record.location() != null) {
            response.setHeader(HttpHeaders.LOCATION, record.location());
        }
        if (record.body() != null) {
            response.getWriter().write(record.body());
            response.getWriter().flush();
        }
    }

    private void safeRelease(String redisKey) {
        try {
            store.release(redisKey);
        } catch (IdempotencyUnavailableException e) {
            // Post-response bookkeeping must never change the response.
            log.warn("Could not release idempotency key {}: {}", redisKey, e.getMessage());
        }
    }

    private void safeComplete(String redisKey, IdempotencyRecord record) {
        try {
            store.complete(redisKey, record);
        } catch (IdempotencyUnavailableException e) {
            // The response is already correct; the key stays IN_PROGRESS until the TTL expires.
            log.warn("Could not complete idempotency key {}: {}", redisKey, e.getMessage());
        }
    }
}
