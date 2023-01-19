package com.concordeu.profile.dto;

import lombok.Builder;

@Builder
public record PaymentDto(
        String customerId,
        String username,
        String customerName) {
}
