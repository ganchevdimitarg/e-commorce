package com.ganchevdimitarg.profile.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDateTime;

@Document(collection = "profiles")
@Getter
@Setter
@Builder
public class Profile {

    @Id
    private String userId;          // shared key from auth
    private String firstName;
    private String lastName;
    private Address address;
    private String phoneNumber;
    private LocalDateTime created;
    private Instant deletedAt;
}
