package com.ganchevdimitarg.payment.gateway;

/** Provider-neutral view of a stored card returned by the {@link PaymentGateway}. */
public record GatewayCard(
        String id,
        String brand,
        String customerId,
        String cvcCheck,
        long expMonth,
        long expYear,
        String lastFourDigits) {
}
