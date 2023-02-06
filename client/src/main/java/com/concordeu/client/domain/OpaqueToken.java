package com.concordeu.client.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("opaque_tokens")
@Data
@Builder
public class OpaqueToken {
    @Id
    private String id;
    private String token;
    private Instant iat;
    private Instant exp;
}
