package com.ganchevdimitarg.order.excaption;

public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
