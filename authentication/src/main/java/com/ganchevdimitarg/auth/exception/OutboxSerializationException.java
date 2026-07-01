package com.ganchevdimitarg.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a domain event cannot be serialised to JSON for the transactional outbox.
 * This is an internal failure — the caller's transaction must roll back rather than
 * commit a domain change whose event can never be published.
 */
public class OutboxSerializationException extends BusinessException {

    public OutboxSerializationException(String message, Throwable cause) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "OUTBOX_SERIALIZATION", message);
        initCause(cause);
    }
}
