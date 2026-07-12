package com.ganchevdimitarg.client.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifies that {@code X-User-Id}/{@code X-User-Roles} were signed by the gateway
 * within the last 30 seconds, closing the header-spoofing gap without requiring
 * every downstream service to re-implement HMAC computation.
 */
public class GatewaySignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";
    private static final long MAX_AGE_MILLIS = 30_000L;

    private final SecretKeySpec key;

    public GatewaySignatureVerifier(String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public boolean isValid(String userId, String roles, String timestamp, String signature) {
        if (timestamp == null || signature == null) {
            return false;
        }
        long timestampMillis;
        try {
            timestampMillis = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(System.currentTimeMillis() - timestampMillis) > MAX_AGE_MILLIS) {
            return false;
        }
        String expected = sign(userId, roles, timestamp);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
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
