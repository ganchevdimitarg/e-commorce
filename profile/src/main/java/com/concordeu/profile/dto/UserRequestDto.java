package com.concordeu.profile.dto;

public record UserRequestDto(
        String username,
        String password,
        String firstName,
        String lastName,
        String phoneNumber,
        String city,
        String street,
        String postCode) {
}
