package com.ganchevdimitarg.order.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a downstream dependency (catalog, profile, payment) is unreachable or its
 * circuit breaker is open. Distinct from a 4xx: the request was well-formed, the
 * dependency is simply unavailable — so callers may retry.
 */
public class ServiceUnavailableException extends BusinessException {
    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", message);
    }
}
