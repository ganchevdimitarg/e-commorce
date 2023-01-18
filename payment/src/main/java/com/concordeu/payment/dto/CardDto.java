package com.concordeu.payment.dto;

import lombok.Builder;

@Builder
public record CardDto(
        String cardId,
        String number,
        long expMonth,
        long expYear,
        String cvc) {
}