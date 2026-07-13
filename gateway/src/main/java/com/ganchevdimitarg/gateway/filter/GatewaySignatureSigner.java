package com.ganchevdimitarg.gateway.filter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Signs the identity headers gateway injects downstream. Must stay byte-for-byte
 * identical to {@code com.ganchevdimitarg.client.security.GatewaySignatureVerifier#sign}
 * — duplicated here because gateway (WebFlux) cannot depend on client
 * (pulls in spring-boot-starter-web). The payload is length-prefixed
 * ({@code <len>:<value>}) per field to prevent delimiter collisions between the
 * user ID and roles values.
 */
public class GatewaySignatureSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private final SecretKeySpec key;

    public GatewaySignatureSigner(String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String sign(String userId, String roles, String timestamp) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            String safeRoles = roles == null ? "" : roles;
            String payload = userId.length() + ":" + userId + "|" + safeRoles.length() + ":" + safeRoles + "|" + timestamp;
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute gateway trust signature", e);
        }
    }
}
