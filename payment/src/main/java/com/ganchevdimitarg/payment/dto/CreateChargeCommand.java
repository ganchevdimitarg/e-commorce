package com.ganchevdimitarg.payment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Request body for charging the authenticated customer's card. The payer is never
 * supplied by the caller — it is resolved from the gateway-injected {@code X-User-Id}
 * in the service layer — so this body only carries the card and the amount.
 */
public record CreateChargeCommand(
        @NotBlank String cardId,
        @Positive @Max(value = 99_999_999, message = "amount exceeds the per-charge limit") long amount,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "currency must be a 3-letter ISO-4217 code") String currency,
        @NotBlank @Email String receiptEmail) {
}
