package com.ganchevdimitarg.payment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for registering a card against the authenticated customer. Only a
 * client-side-tokenised Stripe token/source id is accepted — the raw PAN, CVC and
 * expiry never transit this service, keeping it out of PCI-DSS SAQ-D scope.
 */
public record CreateCardCommand(@NotBlank String token) {
}
