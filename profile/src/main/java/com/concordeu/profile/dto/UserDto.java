package com.concordeu.profile.dto;

import org.springframework.security.core.GrantedAuthority;

import java.util.Set;

public record UserDto(
        String id,
        String username,
        String password,
        Set<? extends GrantedAuthority> grantedAuthorities,
        String firstName,
        String lastName,
        String phoneNumber,
        String city,
        String street,
        String postCode) {
}
