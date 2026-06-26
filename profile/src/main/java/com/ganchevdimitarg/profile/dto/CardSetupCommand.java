package com.ganchevdimitarg.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request payload for attaching a payment card to the authenticated user")
public record CardSetupCommand(

        @NotBlank(message = "Card number must not be blank")
        String cardNumber,

        @NotBlank(message = "Card expiry month must not be blank")
        String cardExpMonth,

        @NotBlank(message = "Card expiry year must not be blank")
        String cardExpYear,

        @NotBlank(message = "Card CVC must not be blank")
        String cardCvc
) {
}
