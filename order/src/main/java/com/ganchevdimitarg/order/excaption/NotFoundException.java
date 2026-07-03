package com.ganchevdimitarg.order.excaption;

import org.springframework.http.HttpStatus;

public class NotFoundException extends BusinessException {
    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }
}
