package com.concordeu.client.common.dto;

import lombok.Builder;

@Builder
public record PaymentDto(
        String username,
        String customerName,
        String customerId,
        long amount,
        String currency,
        String receiptEmail,
        String chargeId,
        String chargeStatus) {
}
