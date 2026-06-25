package com.ganchevdimitarg.auth.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param location filesystem directory holding the persisted RSA signing keypair
 *                 ({@code jwt-signing.key} = PKCS#8 private, {@code jwt-signing.pub} = X.509 public).
 *                 Generated on first run if absent; delivered by Vault in production.
 */
@ConfigurationProperties(prefix = "auth.jwk")
public record JwkProperties(String location) {
}
