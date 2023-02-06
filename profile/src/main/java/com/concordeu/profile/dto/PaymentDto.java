package com.concordeu.profile.dto;

import lombok.Builder;

@Builder
public record PaymentDto(
        String cardId,
        String customerId,
        String username,
        String customerName) {
}
