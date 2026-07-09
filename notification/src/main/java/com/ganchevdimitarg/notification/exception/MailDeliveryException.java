package com.ganchevdimitarg.notification.exception;

import org.springframework.http.HttpStatus;

public class MailDeliveryException extends BusinessException {
    public MailDeliveryException(String recipient) {
        super(HttpStatus.BAD_GATEWAY, "MAIL_DELIVERY_FAILED",
                "Failed to deliver email to " + recipient);
    }
}
