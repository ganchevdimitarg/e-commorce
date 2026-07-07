package com.ganchevdimitarg.payment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** Request body for charging a customer's card. */
public record CreateChargeCommand(
        @NotBlank String username,
        @NotBlank String customerId,
        @NotBlank String cardId,
        @Positive long amount,
        @NotBlank String currency,
        @Email String receiptEmail) {
}
