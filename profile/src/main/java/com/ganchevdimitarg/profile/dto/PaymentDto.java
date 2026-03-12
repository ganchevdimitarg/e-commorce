package com.ganchevdimitarg.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "Payment service customer and card response payload")
@Builder
public record PaymentDto(
        @Schema(description = "Payment card ID", example = "card_456")
        String cardId,

        @Schema(description = "Payment customer ID", example = "cus_123")
        String customerId,

        @Schema(description = "Username associated with the payment customer")
        String username,

        @Schema(description = "Display name of the payment customer")
        String customerName
) {}
