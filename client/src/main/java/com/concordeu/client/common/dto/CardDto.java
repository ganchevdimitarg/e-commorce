package com.concordeu.client.common.dto;

import lombok.Builder;

@Builder
public record CardDto(
        String customerId,
        String cardId,
        String cardNumber,
        long cardExpMonth,
        long cardExpYear,
        String cardCvc) {
}
