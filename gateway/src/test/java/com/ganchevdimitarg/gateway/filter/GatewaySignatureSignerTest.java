package com.ganchevdimitarg.gateway.filter;

import org.junit.jupiter.api.Test;

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
}
