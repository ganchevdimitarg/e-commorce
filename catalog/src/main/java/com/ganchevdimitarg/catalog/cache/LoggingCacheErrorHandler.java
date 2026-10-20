package com.ganchevdimitarg.catalog.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Turns cache-provider failures into cache misses.
 *
 * <p>Reads fall through to the database and write-through puts are skipped, so a Redis
 * outage degrades latency rather than failing requests. Failures are logged without a
 * stack trace: an outage is expected, and the message is enough to alert on.
 */
@Slf4j
public class LoggingCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache {} failed for key {}: {}", "get", key, exception.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("Cache {} failed for key {}: {}", "put", key, exception.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache {} failed for key {}: {}", "evict", key, exception.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("Cache {} failed for key {}: {}", "clear", cache.getName(), exception.getMessage());
    }
}
