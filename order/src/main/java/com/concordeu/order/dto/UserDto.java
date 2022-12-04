package com.concordeu.order.dto;

import org.springframework.security.core.GrantedAuthority;

import java.util.Set;

public record UserDto(
        String username,
        String firstName,
        String lastName,
        String phoneNumber,
        String city,
        String street,
        String postCode) {
}
