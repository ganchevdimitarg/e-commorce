package com.ganchevdimitarg.payment.gateway;

/** Provider-neutral view of a customer returned by the {@link PaymentGateway}. */
public record GatewayCustomer(String id, String email, String name) {
}
