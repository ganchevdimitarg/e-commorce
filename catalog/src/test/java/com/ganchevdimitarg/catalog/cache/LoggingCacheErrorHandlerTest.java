package com.ganchevdimitarg.catalog.cache;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.RedisConnectionFailureException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The cache is an optimisation: a Redis failure must degrade to a cache miss, never break
 * the request.
 */
@Tag("unit")
class LoggingCacheErrorHandlerTest {

    private static final String KEY = "bbbbbbbb-0000-0000-0000-00000000d00d";

    private final CacheErrorHandler handler = new LoggingCacheErrorHandler();
    private final Cache cache = mock(Cache.class);

    @Test
    void should_swallowGetError() {
        assertThatCode(() -> handler.handleCacheGetError(
                new RedisConnectionFailureException("down"), cache, KEY))
                .doesNotThrowAnyException();
    }

    @Test
    void should_swallowPutError() {
        assertThatCode(() -> handler.handleCachePutError(
                new RedisConnectionFailureException("down"), cache, KEY, "value"))
                .doesNotThrowAnyException();
    }

    @Test
    void should_swallowEvictError() {
        assertThatCode(() -> handler.handleCacheEvictError(
                new RedisConnectionFailureException("down"), cache, KEY))
                .doesNotThrowAnyException();
    }

    @Test
    void should_swallowClearError() {
        when(cache.getName()).thenReturn("product");

        assertThatCode(() -> handler.handleCacheClearError(
                new RedisConnectionFailureException("down"), cache))
                .doesNotThrowAnyException();
    }
}
