package com.ganchevdimitarg.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "Card creation request payload sent to payment service")
@Builder
public record CardDto(
        @Schema(description = "Payment customer ID to attach card to", example = "cus_123")
        String customerId,

        @Schema(description = "Card reference ID returned by payment service", example = "card_456")
        String cardId,

        @Schema(description = "Card number", example = "4242424242424242")
        String cardNumber,

        @Schema(description = "Card expiry month", example = "03")
        String cardExpMonth,

        @Schema(description = "Card expiry year", example = "2026")
        String cardExpYear,

        @Schema(description = "Card CVC", example = "314")
        String cardCvc
) {
}