package com.ganchevdimitarg.auth.config.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class KeyManagerTest {


    private static KeyManager keyManager;

    @BeforeAll
    static void setup() {
        keyManager = new KeyManager();
    }

    /**
     * Generates RSA key; asserts non-null keys and valid ID
     */
    @Test
    void generateRsaKey_returnsKeyWithPublicAndPrivateKey_andNonEmptyKeyId() throws JOSEException {
        RSAKey rsaKey = keyManager.generateRsaKey();

        assertNotNull(rsaKey, "RSAKey should not be null");
        assertNotNull(rsaKey.toRSAPublicKey(), "Public key should be present");
        assertNotNull(rsaKey.toRSAPrivateKey(), "Private key should be present");
        assertNotNull(rsaKey.getKeyID(), "keyID should be present");
        assertFalse(rsaKey.getKeyID().isBlank(), "keyID should not be blank");

        RSAPublicKey publicKey = rsaKey.toRSAPublicKey();
        assertTrue(publicKey.getModulus().bitLength() >= 2048,
                "Expected RSA modulus bit length to be at least 2048");
    }

    @Test
    void generateRsaKey_generatesDifferentKeyIdsAcrossCalls() {
        RSAKey first = keyManager.generateRsaKey();
        RSAKey second = keyManager.generateRsaKey();

        assertNotEquals(first.getKeyID(), second.getKeyID(), "Each call should generate a new keyID");
    }

    @Test
    void generateRsaKey_wrapsFailuresInIllegalStateException() {
        // Handles RSA key generation failures; wraps exceptions with cause
        try (MockedStatic<KeyPairGenerator> mocked = mockStatic(KeyPairGenerator.class)) {
            mocked.when(() -> KeyPairGenerator.getInstance("RSA"))
                    .thenThrow(new RuntimeException("boom"));

            IllegalStateException ex = assertThrows(IllegalStateException.class, keyManager::generateRsaKey);
            assertNotNull(ex.getCause(), "IllegalStateException should wrap the original cause");
            assertEquals("boom", ex.getCause().getMessage());
        }
    }
}