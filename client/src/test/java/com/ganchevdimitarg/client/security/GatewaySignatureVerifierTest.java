package com.ganchevdimitarg.client.security;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySignatureVerifierTest {

    private static final String SECRET = "test-shared-secret";
    private final GatewaySignatureVerifier verifier = new GatewaySignatureVerifier(SECRET);

    @Test
    void should_acceptSignature_when_freshAndCorrect() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = verifier.sign("user-1", "ROLE_USER", timestamp);

        assertThat(verifier.isValid("user-1", "ROLE_USER", timestamp, signature)).isTrue();
    }

    @Test
    void should_rejectSignature_when_tampered() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = verifier.sign("user-1", "ROLE_USER", timestamp);

        assertThat(verifier.isValid("user-2", "ROLE_USER", timestamp, signature)).isFalse();
    }

    @Test
    void should_rejectSignature_when_expired() {
        String staleTimestamp = String.valueOf(System.currentTimeMillis() - 31_000L);
        String signature = verifier.sign("user-1", "ROLE_USER", staleTimestamp);

        assertThat(verifier.isValid("user-1", "ROLE_USER", staleTimestamp, signature)).isFalse();
    }

    @Test
    void should_rejectSignature_when_missing() {
        assertThat(verifier.isValid("user-1", "ROLE_USER", "123", null)).isFalse();
    }

    @Test
    void should_rejectSignature_when_timestampMissing() {
        String signature = verifier.sign("user-1", "ROLE_USER", "123");

        assertThat(verifier.isValid("user-1", "ROLE_USER", null, signature)).isFalse();
    }

    @Test
    void should_rejectSignature_when_timestampNotNumeric() {
        assertThat(verifier.isValid("user-1", "ROLE_USER", "not-a-number", "anything")).isFalse();
    }

    @Test
    void should_produceSameSignature_when_rolesIsNull() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = verifier.sign("user-1", null, timestamp);

        assertThat(verifier.isValid("user-1", null, timestamp, signature)).isTrue();
    }

    /**
     * Reference oracle: a literal, independent re-implementation of
     * {@code com.ganchevdimitarg.gateway.filter.GatewaySignatureSigner#sign}, written
     * inline (not calling the gateway class — this module cannot depend on gateway, and
     * a build dependency the other way isn't natural either). Any future drift between
     * the two production implementations will surface here as a mismatch rather than
     * silently breaking cross-service authentication.
     */
    private static String referenceGatewaySign(String secret, String userId, String roles, String timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String safeRoles = roles == null ? "" : roles;
            String payload = userId.length() + ":" + userId + "|" + safeRoles.length() + ":" + safeRoles + "|" + timestamp;
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void should_matchGatewaySignerAlgorithm_when_computingSignature() {
        Stream.of(
                new String[]{"user-1", "ROLE_USER", "1000"},
                new String[]{"user-1", null, "1000"},
                new String[]{"user|1", "ROLE_USER|ROLE_ADMIN", "1000"}
        ).forEach(args -> {
            String userId = args[0];
            String roles = args[1];
            String timestamp = args[2];

            assertThat(verifier.sign(userId, roles, timestamp))
                    .as("userId=%s roles=%s timestamp=%s", userId, roles, timestamp)
                    .isEqualTo(referenceGatewaySign(SECRET, userId, roles, timestamp));
        });
    }
}
