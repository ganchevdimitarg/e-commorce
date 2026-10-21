package com.ganchevdimitarg.catalog.ratelimit;

import io.github.resilience4j.ratelimiter.RateLimiter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies the per-principal, per-bucket isolation of the limiter cache and that its bounded
 * Caffeine store can be swept without failing.
 */
@Tag("unit")
class RateLimiterCacheTest {

    private static final RateLimitProperties LIMITS = new RateLimitProperties(
            true,
            new RateLimitProperties.Limit(2, Duration.ofMinutes(1)),
            new RateLimitProperties.Limit(2, Duration.ofMinutes(1)));

    private final RateLimiterCache cache = new RateLimiterCache(LIMITS);

    @Test
    void should_refuseThirdRequest_when_twoPermitsAreUsed() {
        RateLimiter limiter = cache.get(RateLimitBucket.SEARCH, "alice");

        assertThat(limiter.acquirePermission()).isTrue();
        assertThat(limiter.acquirePermission()).isTrue();
        assertThat(limiter.acquirePermission()).isFalse();
    }

    @Test
    void should_isolateAllowance_when_principalsDiffer() {
        RateLimiter alice = cache.get(RateLimitBucket.SEARCH, "alice");
        RateLimiter bob = cache.get(RateLimitBucket.SEARCH, "bob");

        assertThat(alice).isNotSameAs(bob);
        assertThat(alice.acquirePermission()).isTrue();
        assertThat(alice.acquirePermission()).isTrue();
        assertThat(alice.acquirePermission()).isFalse();
        assertThat(bob.acquirePermission()).isTrue();
    }

    @Test
    void should_isolateAllowance_when_bucketsDiffer() {
        RateLimiter search = cache.get(RateLimitBucket.SEARCH, "alice");
        RateLimiter batchGet = cache.get(RateLimitBucket.BATCH_GET, "alice");

        assertThat(search).isNotSameAs(batchGet);
        assertThat(search.acquirePermission()).isTrue();
        assertThat(search.acquirePermission()).isTrue();
        assertThat(search.acquirePermission()).isFalse();
        assertThat(batchGet.acquirePermission()).isTrue();
    }

    @Test
    void should_keepLimitersUsable_when_expiredEntriesAreSwept() {
        cache.get(RateLimitBucket.SEARCH, "alice");
        cache.get(RateLimitBucket.BATCH_GET, "bob");

        assertThatCode(cache::evictExpired).doesNotThrowAnyException();
        assertThat(cache.cachedLimiters()).isEqualTo(2);
        assertThat(cache.get(RateLimitBucket.SEARCH, "alice").acquirePermission()).isTrue();
    }
}
