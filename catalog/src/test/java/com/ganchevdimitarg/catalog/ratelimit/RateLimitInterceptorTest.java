package com.ganchevdimitarg.catalog.ratelimit;

import com.ganchevdimitarg.catalog.exception.RateLimitExceededException;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that the interceptor only limits annotated handlers, keys the allowance by principal
 * and bucket, honours the kill switch and reports refusals.
 */
@Tag("unit")
class RateLimitInterceptorTest {

    private static final long SEARCH_RETRY_AFTER_SECONDS = 60;

    private static final RateLimitProperties LIMITS = new RateLimitProperties(
            true,
            new RateLimitProperties.Limit(2, Duration.ofMinutes(1)),
            new RateLimitProperties.Limit(1, Duration.ofMinutes(1)));

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final RateLimiterCache cache = new RateLimiterCache(LIMITS);
    private final RateLimitInterceptor interceptor =
            new RateLimitInterceptor(cache, LIMITS, meterRegistry);

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("alice", "n/a", "SCOPE_catalog.read"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_allowRequest_when_handlerHasNoAnnotation() throws Exception {
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(),
                handlerFor("unannotated"))).isTrue();
    }

    @Test
    void should_allowRequest_when_handlerIsNotAHandlerMethod() {
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new Object())).isTrue();
    }

    @Test
    void should_allowRequest_when_rateLimitingIsDisabled() throws Exception {
        RateLimitProperties disabled =
                new RateLimitProperties(false, LIMITS.search(), LIMITS.batchGet());
        RateLimitInterceptor disabledInterceptor =
                new RateLimitInterceptor(cache, disabled, meterRegistry);
        RateLimiter limiter = cache.get(RateLimitBucket.SEARCH, "alice");
        assertThat(limiter.acquirePermission()).isTrue();
        assertThat(limiter.acquirePermission()).isTrue();
        assertThat(limiter.acquirePermission()).isFalse();

        assertThat(disabledInterceptor.preHandle(new MockHttpServletRequest(),
                new MockHttpServletResponse(), handlerFor("annotatedSearch"))).isTrue();
    }

    @Test
    void should_throwRateLimitExceeded_when_searchPermitsAreExhausted() throws Exception {
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(),
                handlerFor("annotatedSearch"))).isTrue();
        assertThat(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(),
                handlerFor("annotatedSearch"))).isTrue();

        assertThatThrownBy(() -> interceptor.preHandle(new MockHttpServletRequest(),
                new MockHttpServletResponse(), handlerFor("annotatedSearch")))
                .isInstanceOfSatisfying(RateLimitExceededException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.getCode()).isEqualTo("RATE_LIMIT_EXCEEDED");
                    assertThat(ex.getMessage()).contains("search");
                    assertThat(ex.getRetryAfterSeconds())
                            .isEqualTo(SEARCH_RETRY_AFTER_SECONDS);
                });
        assertThat(meterRegistry.get(RateLimitInterceptor.REJECTED_METRIC)
                .tag("bucket", "search")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    void should_limitBatchGetSeparately_when_searchHasItsOwnAllowance() throws Exception {
        RateLimiter search = cache.get(RateLimitBucket.SEARCH, "alice");
        assertThat(search.acquirePermission()).isTrue();
        assertThat(search.acquirePermission()).isTrue();
        assertThat(search.acquirePermission()).isFalse();

        assertThat(interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(),
                handlerFor("annotatedBatchGet"))).isTrue();
    }

    private HandlerMethod handlerFor(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new SampleHandler(), SampleHandler.class.getMethod(methodName));
    }

    /** Minimal handler used only to carry method annotations into the interceptor. */
    static class SampleHandler {

        public void unannotated() {
        }

        @RateLimited(RateLimitBucket.SEARCH)
        public void annotatedSearch() {
        }

        @RateLimited(RateLimitBucket.BATCH_GET)
        public void annotatedBatchGet() {
        }
    }
}
