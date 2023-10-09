package com.concordeu.profile.dto;

import lombok.Builder;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Mono;

import java.util.Set;

@Builder
public record UserDto(
        String id,
        String username,
        String password,
        Set<SimpleGrantedAuthority> grantedAuthorities,
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
