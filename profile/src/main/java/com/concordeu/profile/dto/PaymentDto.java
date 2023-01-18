package com.concordeu.profile.dto;

import lombok.Builder;

@Builder
public record PaymentDto(
        String username,
        String customerName,
        String customerId,
        String number,
        long expMonth,
        long expYear,
        String cvc,
        String cardId) {
}
