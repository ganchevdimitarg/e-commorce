package com.ganchevdimitarg.profile.exception;

/**
 * A downstream dependency (payment service) is unreachable or its circuit breaker is
 * open. Maps to 503 so callers can distinguish an outage from a bad request.
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
