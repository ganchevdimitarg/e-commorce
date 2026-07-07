package com.ganchevdimitarg.payment.gateway;

/** Parameters for creating a charge through the {@link PaymentGateway}. */
public record ChargeRequest(
        long amount,
        String currency,
        String receiptEmail,
        String customerId,
        String source) {
}
