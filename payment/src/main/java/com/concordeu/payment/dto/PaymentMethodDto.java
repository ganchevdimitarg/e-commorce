package com.concordeu.payment.dto;

import lombok.Builder;

@Builder
public record PaymentMethodDto(
        String number,
        long expMonth,
        long expYear,
        String cvc) {
}
