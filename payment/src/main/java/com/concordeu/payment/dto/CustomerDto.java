package com.concordeu.payment.dto;

import lombok.Builder;

@Builder
public record CustomerDto(
        String username,
        String customerName,
        String customerId) {
}
