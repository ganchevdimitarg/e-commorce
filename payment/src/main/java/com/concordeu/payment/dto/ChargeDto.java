package com.concordeu.payment.dto;

import lombok.Builder;

@Builder
public record ChargeDto(
        String id,
        long amount,
        String currency,
        String paymentMethod,
        String status,
        String source) {


}
