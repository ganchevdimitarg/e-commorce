package com.concordeu.auth.entities;

import com.nimbusds.jose.jwk.RSAKey;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("rsa_key")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class RSA {
    @Id
    private String id;
    private RSAKey key;
}
