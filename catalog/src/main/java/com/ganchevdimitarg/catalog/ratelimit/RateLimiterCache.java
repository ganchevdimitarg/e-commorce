package com.ganchevdimitarg.catalog.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Keeps one Resilience4j {@link RateLimiter} per bucket and principal.
 *
 * <p>The Caffeine store is bounded so a long tail of one-off principals cannot grow the map
 * without limit; an entry expires after two refresh periods without access. The permission
 * timeout is zero: a refused request fails fast instead of parking a virtual thread until the
 * next refresh.
 */
@Component
public class RateLimiterCache {

    private static final long MAX_LIMITERS = 10_000;

    private final RateLimitProperties properties;
    private final Cache<String, RateLimiter> limiters;

    public RateLimiterCache(RateLimitProperties properties) {
        this.properties = properties;
        Duration longest = properties.search().period().compareTo(properties.batchGet().period()) >= 0
                ? properties.search().period()
                : properties.batchGet().period();
        this.limiters = Caffeine.newBuilder()
                .maximumSize(MAX_LIMITERS)
                .expireAfterAccess(longest.multipliedBy(2))
                .build();
    }

    /** Returns the limiter for a bucket and principal, creating it on first use. */
    public RateLimiter get(RateLimitBucket bucket, String principal) {
        return limiters.get(key(bucket, principal), key -> RateLimiter.of(key, configFor(bucket)));
    }

    private RateLimiterConfig configFor(RateLimitBucket bucket) {
        RateLimitProperties.Limit limit = properties.limitFor(bucket);
        return RateLimiterConfig.custom()
                .limitRefreshPeriod(limit.period())
                .limitForPeriod(limit.permits())
                .timeoutDuration(Duration.ZERO)
                .build();
    }

    private static String key(RateLimitBucket bucket, String principal) {
        return bucket.label() + ":" + principal;
    }

    /** Visible for tests: forces Caffeine to drop expired entries. */
    void evictExpired() {
        limiters.cleanUp();
    }

    /** Visible for tests: cached limiters, including expired entries not yet swept. */
    long cachedLimiters() {
        return limiters.estimatedSize();
    }
}
