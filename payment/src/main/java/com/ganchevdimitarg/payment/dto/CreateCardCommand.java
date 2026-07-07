package com.ganchevdimitarg.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** Request body for registering a card against a provider customer. */
public record CreateCardCommand(
        @NotBlank String customerId,
        @NotBlank String cardNumber,
        @Positive long cardExpMonth,
        @Positive long cardExpYear,
        @NotBlank String cardCvc) {
}
