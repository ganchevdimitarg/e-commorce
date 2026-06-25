package com.ganchevdimitarg.auth.exception;

import org.springframework.http.HttpStatus;

/** Raised when persisted client config is internally inconsistent (e.g. missing token settings). */
public class ClientConfigurationException extends BusinessException {
    public ClientConfigurationException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "CLIENT_MISCONFIGURED", message);
    }
}
