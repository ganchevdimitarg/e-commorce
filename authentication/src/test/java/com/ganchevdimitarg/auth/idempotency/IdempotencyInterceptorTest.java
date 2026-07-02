package com.ganchevdimitarg.auth.idempotency;

import com.ganchevdimitarg.auth.exception.ConflictException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdempotencyInterceptorTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOperations;
    private IdempotencyInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOperations);
        interceptor = new IdempotencyInterceptor(redis);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
    }

    @Test
    void should_proceed_when_idempotencyKeyHeaderAbsent() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Idempotency-Key")).thenReturn(null);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    void should_proceed_when_methodNotGuarded() {
        when(request.getMethod()).thenReturn("GET");

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    void should_proceed_when_idempotencyKeyFirstSeen() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Idempotency-Key")).thenReturn("fresh-key");
        when(valueOperations.setIfAbsent(eq("auth:idempotency:fresh-key"), anyString(), any(Duration.class)))
                .thenReturn(true);

        boolean result = interceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    void should_throwConflict_when_idempotencyKeyAlreadySeen() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Idempotency-Key")).thenReturn("dupe-key");
        when(valueOperations.setIfAbsent(eq("auth:idempotency:dupe-key"), anyString(), any(Duration.class)))
                .thenReturn(false);

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("dupe-key");
    }
}
