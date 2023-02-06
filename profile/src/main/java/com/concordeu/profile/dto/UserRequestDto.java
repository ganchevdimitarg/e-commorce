package com.concordeu.profile.dto;

import lombok.Builder;

@Builder
public record UserRequestDto(
        String username,
        String password,
        String firstName,
        String lastName,
        String phoneNumber,
        String city,
        String street,
        String postCode,
        String cardNumber,
        long cardExpMonth,
        long cardExpYear,
        String cardCvc) {
}
