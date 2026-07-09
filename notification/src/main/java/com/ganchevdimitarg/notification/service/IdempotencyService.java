package com.ganchevdimitarg.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed guard so a redelivered event (or a retried request) does not send a second
 * email. The key is claimed atomically before the action runs; if the action fails the key
 * is released so the retry / DLT path can re-process rather than being permanently suppressed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);
    private static final String KEY_PREFIX = "idempotency:notification:";

    private final StringRedisTemplate redisTemplate;

    public void runOnce(String key, Runnable action) {
        String redisKey = KEY_PREFIX + key;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", TTL);
        if (Boolean.FALSE.equals(acquired)) {
            log.info("Duplicate notification suppressed for key {}", key);
            return;
        }
        try {
            action.run();
        } catch (RuntimeException e) {
            redisTemplate.delete(redisKey);
            throw e;
        }
    }
}
