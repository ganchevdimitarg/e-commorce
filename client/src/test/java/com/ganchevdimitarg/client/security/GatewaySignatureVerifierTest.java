package com.ganchevdimitarg.client.security;

import org.junit.jupiter.api.Test;

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
}
