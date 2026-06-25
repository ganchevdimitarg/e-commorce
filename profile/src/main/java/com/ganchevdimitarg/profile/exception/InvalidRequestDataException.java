package com.ganchevdimitarg.profile.exception;

public class InvalidRequestDataException extends RuntimeException {

    public InvalidRequestDataException(String message) {
        super(message);
    }

    public InvalidRequestDataException(String message, Throwable cause) {
        super(message, cause);
    }
}