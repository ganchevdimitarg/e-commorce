package com.ganchevdimitarg.catalog.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Raised when a principal has exhausted a rate-limit bucket.
 *
 * <p>The bucket arrives as a label rather than a {@code RateLimitBucket} so the exception
 * package does not depend on the rate-limit package (the dependency runs the other way).
 */
@Getter
public class RateLimitExceededException extends BusinessException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String bucketLabel, long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                "Too many " + bucketLabel + " requests; retry after "
                        + retryAfterSeconds + " seconds");
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
