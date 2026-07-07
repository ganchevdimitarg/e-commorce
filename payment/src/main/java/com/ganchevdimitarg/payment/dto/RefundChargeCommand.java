package com.ganchevdimitarg.payment.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for refunding a charge in full. */
public record RefundChargeCommand(@NotBlank String chargeId) {
}
