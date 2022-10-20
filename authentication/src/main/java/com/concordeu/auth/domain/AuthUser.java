package com.concordeu.auth.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;

import java.time.LocalDateTime;
import java.util.Set;

@Document("users")
@Data
@Builder
public class AuthUser {
    @Id
    private String id;
    private String username;
    private String password;
    private Set<? extends GrantedAuthority> grantedAuthorities;
    private String firstName;
    private String lastName;
    private Address address;
    @Indexed(unique = true)
    private String email;
    private String phoneNumber;
    private LocalDateTime created;
}
