package com.ganchevdimitarg.order.service;

import com.ganchevdimitarg.order.exception.ConflictException;
import com.ganchevdimitarg.order.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.integration.redis.util.RedisLockRegistry;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

/**
 * Idempotency guard for mutating, side-effecting order operations. The first request for a
 * given {@code Idempotency-Key} runs the action under a distributed lock and caches its
 * result for 24h; replays return the cached result without re-running the action — this is
 * what prevents a retried create from charging the customer twice.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:order:";
    private static final Duration TTL = Duration.ofHours(24);
    private static final long LOCK_WAIT_SECONDS = 5;

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisLockRegistry lockRegistry;

    public <T> T execute(String idempotencyKey, Class<T> type, Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationException("Idempotency-Key header is required");
        }
        String cacheKey = KEY_PREFIX + idempotencyKey;

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Idempotent replay for key {}", idempotencyKey);
            return type.cast(cached);
        }

        Lock lock = lockRegistry.obtain(idempotencyKey);
        boolean acquired;
        try {
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ConflictException("Interrupted acquiring idempotency lock for " + idempotencyKey);
        }
        if (!acquired) {
            throw new ConflictException("A request with Idempotency-Key " + idempotencyKey
                    + " is already in progress");
        }
        try {
            Object recheck = redisTemplate.opsForValue().get(cacheKey);
            if (recheck != null) {
                return type.cast(recheck);
            }
            T result = action.get();
            redisTemplate.opsForValue().set(cacheKey, result, TTL);
            return result;
        } finally {
            lock.unlock();
        }
    }
}
