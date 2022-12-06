package com.concordeu.auth.dto;

public record AuthUserRequestDto(
        String username,
        String password,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        String city,
        String street,
        String postCode) {
}
