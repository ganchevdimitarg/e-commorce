package com.concordeu.domain;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
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
