package com.concordeu.payment.dto;

import lombok.Builder;

@Builder
public record PaymentDto(
        String username,
        String customerName,
        String customerId,
        String cardNumber,
        long cardExpMonth,
        long cardExpYear,
        String cardCvc,
        long amount,
        String currency,
        String receiptEmail,
        String cardId,
        String chargeId,
        String chargeStatus) {
}
