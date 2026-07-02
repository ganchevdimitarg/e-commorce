package com.ganchevdimitarg.auth.config.security;

import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

@Component
@RequiredArgsConstructor
public class KeyManager {

    private static final String PRIVATE_FILE = "jwt-signing.key";
    private static final String PUBLIC_FILE = "jwt-signing.pub";

    private final JwkProperties properties;

    /**
     * Loads the RSA signing key from {@code properties.location()}, generating and persisting it on
     * first run if absent. The keyID is the public-key SHA-256 thumbprint, so it is identical across
     * restarts and across instances that share the directory — a prerequisite for stable JWT signing.
     */
    public RSAKey loadRsaKey() {
        try {
            Path dir = Path.of(properties.location());
            Path privatePath = dir.resolve(PRIVATE_FILE);
            Path publicPath = dir.resolve(PUBLIC_FILE);

            RSAPublicKey publicKey;
            RSAPrivateKey privateKey;
            if (Files.exists(privatePath) && Files.exists(publicPath)) {
                KeyFactory rsa = KeyFactory.getInstance("RSA");
                publicKey = (RSAPublicKey) rsa.generatePublic(
                        new X509EncodedKeySpec(Files.readAllBytes(publicPath)));
                privateKey = (RSAPrivateKey) rsa.generatePrivate(
                        new PKCS8EncodedKeySpec(Files.readAllBytes(privatePath)));
            } else {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair pair = generator.generateKeyPair();
                publicKey = (RSAPublicKey) pair.getPublic();
                privateKey = (RSAPrivateKey) pair.getPrivate();
                Files.createDirectories(dir);
                Files.write(publicPath, new X509EncodedKeySpec(publicKey.getEncoded()).getEncoded());
                Files.write(privatePath, new PKCS8EncodedKeySpec(privateKey.getEncoded()).getEncoded());
            }

            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyIDFromThumbprint()
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to load or generate JWK signing key", ex);
        }
    }
}
