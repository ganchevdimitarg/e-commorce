package com.concordeu.notification.excaption;

public class InvalidRequestDataException extends RuntimeException {

    public InvalidRequestDataException(String message) {
        super(message);
    }
}
