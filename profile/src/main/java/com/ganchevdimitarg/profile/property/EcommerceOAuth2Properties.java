package com.ganchevdimitarg.profile.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ecommerce.oauth2")
public record EcommerceOAuth2Properties(String clientId, String secret) {}