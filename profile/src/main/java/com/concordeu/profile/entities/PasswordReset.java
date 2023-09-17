package com.concordeu.profile.entities;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.OffsetDateTime;

@Document(collection = "password_reset")
@Data
@Builder
public class PasswordReset {
    @Id
    private String id;
    private String token;
    private String username;
    private OffsetDateTime createdOn;
}
