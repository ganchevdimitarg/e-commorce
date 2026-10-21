package com.ganchevdimitarg.catalog.ratelimit;

/**
 * The independently limited product endpoints.
 *
 * <p>Each bucket owns one limit configuration and one limiter per principal, so exhausting the
 * search allowance never blocks a batch-get.
 */
public enum RateLimitBucket {

    SEARCH("search"),
    BATCH_GET("batch-get");

    private final String label;

    RateLimitBucket(String label) {
        this.label = label;
    }

    /** Lower-case name used in problem details and as the rejected-requests metric tag. */
    public String label() {
        return label;
    }
}
