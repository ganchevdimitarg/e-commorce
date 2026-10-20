package com.ganchevdimitarg.catalog.idempotency;

/**
 * Signals that the idempotency store (Redis) could not be reached or did not answer. The
 * interceptor treats this as fail-open per ADR-006: the request proceeds unprotected.
 */
public class IdempotencyUnavailableException extends RuntimeException {

    public IdempotencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
