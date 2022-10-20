package com.concordeu.auth.dto;

import lombok.*;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@ToString
public class AuthUserDto {
    private String id;
    private String username;
    private String password;
    private Set<? extends GrantedAuthority> grantedAuthorities;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String city;
    private String street;
    private String postCode;
}
