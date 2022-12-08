package com.concordeu.order.dto;

import lombok.Builder;

@Builder
public record UserDto(
        String username,
        String firstName,
        String lastName,
        String phoneNumber,
        String city,
        String street,
        String postCode) {
}
