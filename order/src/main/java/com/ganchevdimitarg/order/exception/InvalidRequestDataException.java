package com.ganchevdimitarg.order.exception;

import org.springframework.http.HttpStatus;

public class InvalidRequestDataException extends BusinessException {

    public InvalidRequestDataException(String message) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
