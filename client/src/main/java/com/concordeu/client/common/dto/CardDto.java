package com.concordeu.client.common.dto;

import lombok.Builder;

@Builder
public record CardDto(
        String cardId,
        String customerId,
        String cardNumber,
        long cardExpMonth,
        long cardExpYear,
        String cardCvc) {
}
