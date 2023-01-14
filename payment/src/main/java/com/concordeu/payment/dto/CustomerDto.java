package com.concordeu.payment.dto;

import lombok.Builder;

@Builder
public record CustomerDto(
        String email,
        String customerName,
        String customerId,
        String paymentMethod) {
}
