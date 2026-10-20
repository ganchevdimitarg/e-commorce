package com.ganchevdimitarg.catalog.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.catalog.idempotency.IdempotencyStore.ClaimResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Pins the claim state machine and, in particular, the translation of Redis failures into
 * {@link IdempotencyUnavailableException} that makes fail-open possible (ADR-006).
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class IdempotencyStoreTest {

    private static final String KEY = "catalog:idempotency:" + "a".repeat(64);

    @Mock
    StringRedisTemplate redis;

    @Mock
    ValueOperations<String, String> ops;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private IdempotencyStore store;

    @BeforeEach
    void setUp() {
        store = new IdempotencyStore(redis, objectMapper);
    }

    @Test
    void should_returnClaimed_when_setIfAbsentSucceeds() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(eq(KEY), anyString(), eq(IdempotencyStore.TTL))).thenReturn(true);

        assertThat(store.claim(KEY)).isInstanceOf(ClaimResult.Claimed.class);
    }

    @Test
    void should_returnInProgress_when_setIfAbsentFailsAndStoredValueIsNull() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(eq(KEY), anyString(), eq(IdempotencyStore.TTL))).thenReturn(false);
        when(ops.get(KEY)).thenReturn(null);

        assertThat(store.claim(KEY)).isInstanceOf(ClaimResult.InProgress.class);
    }

    @Test
    void should_returnInProgress_when_storedValueIsLegacyOrCorrupt() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(eq(KEY), anyString(), eq(IdempotencyStore.TTL))).thenReturn(false);
        when(ops.get(KEY)).thenReturn("1");

        assertThat(store.claim(KEY)).isInstanceOf(ClaimResult.InProgress.class);
    }

    @Test
    void should_returnInProgress_when_storedRecordIsInProgress() throws Exception {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(eq(KEY), anyString(), eq(IdempotencyStore.TTL))).thenReturn(false);
        when(ops.get(KEY)).thenReturn(objectMapper.writeValueAsString(IdempotencyRecord.inProgress()));

        assertThat(store.claim(KEY)).isInstanceOf(ClaimResult.InProgress.class);
    }

    @Test
    void should_returnCompleted_when_storedRecordIsCompleted() throws Exception {
        IdempotencyRecord record = IdempotencyRecord.completed(
                201, "application/json", "/api/v1/catalog/products/1", "{\"id\":1}");
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(eq(KEY), anyString(), eq(IdempotencyStore.TTL))).thenReturn(false);
        when(ops.get(KEY)).thenReturn(objectMapper.writeValueAsString(record));

        ClaimResult result = store.claim(KEY);

        assertThat(result).isInstanceOf(ClaimResult.Completed.class);
        assertThat(((ClaimResult.Completed) result).record()).isEqualTo(record);
    }

    @Test
    void should_throwIdempotencyUnavailable_when_setIfAbsentFailsOnRedis() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(eq(KEY), anyString(), eq(IdempotencyStore.TTL)))
                .thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThatThrownBy(() -> store.claim(KEY))
                .isInstanceOf(IdempotencyUnavailableException.class)
                .hasCauseInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    void should_throwIdempotencyUnavailable_when_getFailsOnRedis() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(eq(KEY), anyString(), eq(IdempotencyStore.TTL))).thenReturn(false);
        when(ops.get(KEY)).thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThatThrownBy(() -> store.claim(KEY))
                .isInstanceOf(IdempotencyUnavailableException.class)
                .hasCauseInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    void should_throwIdempotencyUnavailable_when_completeFailsOnRedis() {
        when(redis.opsForValue()).thenReturn(ops);
        doThrow(new RedisConnectionFailureException("connection refused"))
                .when(ops).set(eq(KEY), anyString(), eq(IdempotencyStore.TTL));

        assertThatThrownBy(() -> store.complete(KEY, IdempotencyRecord.completed(200, null, null, null)))
                .isInstanceOf(IdempotencyUnavailableException.class)
                .hasCauseInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    void should_throwIdempotencyUnavailable_when_releaseFailsOnRedis() {
        when(redis.delete(KEY)).thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThatThrownBy(() -> store.release(KEY))
                .isInstanceOf(IdempotencyUnavailableException.class)
                .hasCauseInstanceOf(RedisConnectionFailureException.class);
    }
}
