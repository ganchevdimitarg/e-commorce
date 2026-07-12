package com.ganchevdimitarg.gateway.filter;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GatewaySignatureSignerTest {

    @Test
    void should_produceSameSignature_when_calledTwiceWithSameInputs() {
        GatewaySignatureSigner signer = new GatewaySignatureSigner("test-shared-secret");

        String first = signer.sign("user-1", "ROLE_USER", "1000");
        String second = signer.sign("user-1", "ROLE_USER", "1000");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void should_produceDifferentSignature_when_userIdDiffers() {
        GatewaySignatureSigner signer = new GatewaySignatureSigner("test-shared-secret");

        assertThat(signer.sign("user-1", "ROLE_USER", "1000"))
                .isNotEqualTo(signer.sign("user-2", "ROLE_USER", "1000"));
    }

    /**
     * Reference oracle: a literal, independent re-implementation of
     * {@code com.ganchevdimitarg.client.security.GatewaySignatureVerifier#sign}, written
     * inline (not calling the client class — this module cannot depend on client). Any
     * future drift between the two production implementations will surface here as a
     * mismatch rather than silently breaking cross-service authentication.
     */
    private static String referenceClientSign(String secret, String userId, String roles, String timestamp) {
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
    void should_matchClientVerifierAlgorithm_when_computingSignature() {
        String secret = "test-shared-secret";
        GatewaySignatureSigner signer = new GatewaySignatureSigner(secret);

        Stream.of(
                new String[]{"user-1", "ROLE_USER", "1000"},
                new String[]{"user-1", null, "1000"},
                new String[]{"user|1", "ROLE_USER|ROLE_ADMIN", "1000"}
        ).forEach(args -> {
            String userId = args[0];
            String roles = args[1];
            String timestamp = args[2];

            assertThat(signer.sign(userId, roles, timestamp))
                    .as("userId=%s roles=%s timestamp=%s", userId, roles, timestamp)
                    .isEqualTo(referenceClientSign(secret, userId, roles, timestamp));
        });
    }
}
