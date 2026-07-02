package com.ganchevdimitarg.auth.config.security;

import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class KeyManagerTest {

    @TempDir
    Path keyDir;

    private KeyManager keyManager;

    @BeforeEach
    void setUp() {
        keyManager = new KeyManager(new JwkProperties(keyDir.toString()));
    }

    @Test
    void should_generatePersistAndReloadKeyWithStableId_when_directoryEmpty() throws Exception {
        RSAKey generated = keyManager.loadRsaKey();          // first call generates + persists
        RSAKey reloaded = new KeyManager(new JwkProperties(keyDir.toString())).loadRsaKey(); // reads from disk

        assertThat(generated.toRSAPublicKey()).isNotNull();
        assertThat(generated.toRSAPrivateKey()).isNotNull();
        assertThat(generated.toRSAPublicKey().getModulus().bitLength()).isGreaterThanOrEqualTo(2048);
        assertThat(generated.getKeyID()).isNotBlank();
        assertThat(reloaded.getKeyID())
                .as("keyID must be stable across restarts (same persisted key)")
                .isEqualTo(generated.getKeyID());
        assertThat(reloaded.toRSAPublicKey().getModulus())
                .isEqualTo(generated.toRSAPublicKey().getModulus());
    }
}
