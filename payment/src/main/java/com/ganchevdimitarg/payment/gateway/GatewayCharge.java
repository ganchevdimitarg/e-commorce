package com.ganchevdimitarg.payment.gateway;

/** Provider-neutral view of a charge returned by the {@link PaymentGateway}. */
public record GatewayCharge(
        String id,
        long amount,
        String currency,
        String customerId,
        String receiptEmail,
        String status) {
}
