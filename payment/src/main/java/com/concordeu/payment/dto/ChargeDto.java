package com.concordeu.payment.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record ChargeDto(
        BigDecimal amount,
        String currency,
        String customerId,
        String receiptEmail) {
}
