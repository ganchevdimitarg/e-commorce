package com.ganchevdimitarg.catalog.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Redis-backed idempotency state machine.
 *
 * <p>A key starts as {@link IdempotencyRecord.State#IN_PROGRESS} when claimed, is replaced by a
 * {@link IdempotencyRecord.State#COMPLETED} record carrying the response when the request
 * succeeds, and is deleted when the request fails with a 5xx or an unhandled exception.
 *
 * <p>Every Redis failure is translated to {@link IdempotencyUnavailableException}; the caller
 * (the interceptor) decides whether to fail open or ignore the error.
 */
public final class IdempotencyStore {

    static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public IdempotencyStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public ClaimResult claim(String redisKey) {
        String inProgress = serialize(IdempotencyRecord.inProgress());
        Boolean claimed = redisCall(
                () -> redis.opsForValue().setIfAbsent(redisKey, inProgress, TTL),
                "claim idempotency key");
        if (Boolean.TRUE.equals(claimed)) {
            return new ClaimResult.Claimed();
        }

        String stored = redisCall(() -> redis.opsForValue().get(redisKey), "read idempotency key");
        if (stored == null) {
            // The key expired between setIfAbsent and get; treat it as still in progress rather
            // than executing the request twice.
            return new ClaimResult.InProgress();
        }
        IdempotencyRecord record;
        try {
            record = objectMapper.readValue(stored, IdempotencyRecord.class);
        } catch (JsonProcessingException e) {
            // Unparseable values are corrupt; never replay or re-execute an ambiguous request.
            // Old latch entries lived under a different key space and are never read here.
            return new ClaimResult.InProgress();
        }
        return record.state() == IdempotencyRecord.State.COMPLETED
                ? new ClaimResult.Completed(record)
                : new ClaimResult.InProgress();
    }

    public void complete(String redisKey, IdempotencyRecord record) {
        String serialized = serialize(record);
        redisCall(() -> {
            redis.opsForValue().set(redisKey, serialized, TTL);
            return null;
        }, "complete idempotency key");
    }

    public void release(String redisKey) {
        redisCall(() -> redis.delete(redisKey), "release idempotency key");
    }

    private String serialize(IdempotencyRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise idempotency record", e);
        }
    }

    private static <T> T redisCall(Supplier<T> operation, String action) {
        try {
            return operation.get();
        } catch (RuntimeException e) {
            throw new IdempotencyUnavailableException("Could not " + action + ": " + e.getMessage(), e);
        }
    }

    public sealed interface ClaimResult
            permits ClaimResult.Claimed, ClaimResult.InProgress, ClaimResult.Completed {

        record Claimed() implements ClaimResult {
        }

        record InProgress() implements ClaimResult {
        }

        record Completed(IdempotencyRecord record) implements ClaimResult {
        }
    }
}
