package com.concordeu.profile.dto;

import lombok.Builder;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;

@Builder
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
        String postCode,
        String cardNumber,
        long cardExpMonth,
        long cardExpYear,
        String cardCvc,
        String cardId) {
}
