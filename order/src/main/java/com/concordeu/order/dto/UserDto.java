package com.concordeu.order.dto;

import lombok.Builder;

@Builder
public record UserDto(
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
        String cardCvc,
        String cardId) {
}
