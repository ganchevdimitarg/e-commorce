package com.ganchevdimitarg.catalog.exception;

import org.springframework.http.HttpStatus;

/**
 * A client-supplied {@code If-Match} value does not match the current version of the
 * resource. Mapped to 412 problem+json by {@code ControllerExceptionHandler}; the client
 * should refresh the representation and retry with the new strong ETag.
 */
public class PreconditionFailedException extends BusinessException {

    public PreconditionFailedException(String resource, Object id) {
        super(HttpStatus.PRECONDITION_FAILED, "PRECONDITION_FAILED",
                resource + " was modified; refresh and retry: " + id);
    }
}
