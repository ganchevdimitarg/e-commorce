package com.ganchevdimitarg.order.exception;

import org.springframework.http.HttpStatus;

public class ValidationException extends BusinessException {
    public ValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
