package com.ganchevdimitarg.order.excaption;

public class InvalidRequestDataException extends RuntimeException {

    public InvalidRequestDataException(String message) {
        super(message);
    }
}
