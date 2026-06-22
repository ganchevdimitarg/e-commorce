package com.concordeu.catalog.idempotency;

import com.concordeu.catalog.exception.ConflictException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
class IdempotencyInterceptorTest {

    @Test
    void should_allow_when_keyIsNew() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(eq("catalog:idempotency:k1"), any(), any(Duration.class))).thenReturn(true);

        IdempotencyInterceptor interceptor = new IdempotencyInterceptor(redis);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/catalog/product/create-product");
        request.addHeader("Idempotency-Key", "k1");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }

    @Test
    void should_throwConflict_when_keyAlreadySeen() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(eq("catalog:idempotency:k1"), any(), any(Duration.class))).thenReturn(false);

        IdempotencyInterceptor interceptor = new IdempotencyInterceptor(redis);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/x");
        request.addHeader("Idempotency-Key", "k1");

        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void should_skip_when_getRequest() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        IdempotencyInterceptor interceptor = new IdempotencyInterceptor(redis);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/x");
        request.addHeader("Idempotency-Key", "k1");

        assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
    }
}
