package com.ganchevdimitarg.payment.gateway;

/** Raw card details supplied when registering a card with the {@link PaymentGateway}. */
public record CardDetails(String number, long expMonth, long expYear, String cvc) {
}
