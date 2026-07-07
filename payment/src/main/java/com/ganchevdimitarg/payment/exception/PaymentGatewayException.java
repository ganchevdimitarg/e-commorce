package com.ganchevdimitarg.payment.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when the downstream payment provider (Stripe) fails. Surfaces as a
 * 502 problem+json response rather than leaking the provider's checked
 * {@code StripeException} through the web layer.
 */
public class PaymentGatewayException extends BusinessException {

    public PaymentGatewayException(String message) {
        super(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR", message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR", message, cause);
    }
}
