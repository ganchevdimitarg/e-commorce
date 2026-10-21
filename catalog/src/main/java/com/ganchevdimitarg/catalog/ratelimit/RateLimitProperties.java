package com.ganchevdimitarg.catalog.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Per-principal rate limits for the product read endpoints.
 *
 * <p>The defaults are 60 search requests and 10 batch-get requests per minute, in independent
 * buckets. {@code enabled} defaults to {@code true}, so a service that omits the key is still
 * protected; set {@code catalog.rate-limit.enabled=false} to bypass the interceptor.
 */
@ConfigurationProperties(prefix = "catalog.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        Limit search,
        Limit batchGet) {

    private static final Limit DEFAULT_SEARCH = new Limit(60, Duration.ofMinutes(1));
    private static final Limit DEFAULT_BATCH_GET = new Limit(10, Duration.ofMinutes(1));

    public RateLimitProperties {
        search = search == null ? DEFAULT_SEARCH : search;
        batchGet = batchGet == null ? DEFAULT_BATCH_GET : batchGet;
    }

    /** The limit that applies to a bucket. */
    public Limit limitFor(RateLimitBucket bucket) {
        return switch (bucket) {
            case SEARCH -> search;
            case BATCH_GET -> batchGet;
        };
    }

    /** One bucket's allowance: {@code permits} requests per {@code period}. */
    public record Limit(int permits, Duration period) {
    }
}
