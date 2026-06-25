package com.ganchevdimitarg.profile.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.service")
public record PaymentServiceProperties(
        CustomerUris customer,
        CardUris card
) {
    public record CustomerUris(String post, String get, String delete) {}
    public record CardUris(String post, String get) {}
}