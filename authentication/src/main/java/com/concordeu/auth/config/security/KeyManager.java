package com.concordeu.auth.config.security;

import com.concordeu.auth.entities.RSA;
import com.concordeu.auth.repository.RSAKeyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.RSAKey;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class KeyManager {
    private final ObjectMapper objectMapper;
    private final RSAKeyRepository rsaKeyRepository;

    @SneakyThrows
    public RSAKey generateRsaKey() {
        if (rsaKeyRepository.count() == 0) {
            KeyPair keyPair = generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
            RSAKey rsaKey = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(UUID.randomUUID().toString())
                    .build();
//            rsaKeyRepository.save(RSA.builder().key(rsaKey).build());
            rsaKeyRepository.save(RSA.builder().key(rsaKey.toJSONString()).build());
            return rsaKey;
        }
//        return rsaKeyRepository.findAll().get(0).getKey();
        return objectMapper.readValue(rsaKeyRepository.findAll().get(0).getKey(), RSAKey.class);
    }

    private KeyPair generateKeyPair() {
        KeyPair keyPair;
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            keyPair = keyPairGenerator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        return keyPair;
    }
}
