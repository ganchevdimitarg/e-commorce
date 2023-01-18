package com.concordeu.order.dto;

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
        long amount,
        String currency,
        String receiptEmail,
        String cardId,
        String chargeId,
        String chargeStatus,
        String source) {
}
