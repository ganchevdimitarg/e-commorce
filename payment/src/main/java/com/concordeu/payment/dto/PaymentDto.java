package com.concordeu.payment.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record PaymentDto(
        String username,
        String customerName,
        String customerId,
        String number,
        long expMonth,
        long expYear,
        String cvc,
        BigDecimal amount,
        String currency,
        String receiptEmail,
        String cardId) {
}
