package com.ganchevdimitarg.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;

import java.util.List;

/**
 * Order creation payload. Deliberately carries <strong>no</strong> card or personal data:
 * the customer and their payment card must be registered with the profile/payment services
 * before ordering, and the charge uses the {@code cardId} resolved from the profile lookup.
 * Keeping PAN/CVC out of this service keeps it out of PCI scope.
 */
@Builder
public record OrderDto(
        @NotBlank String username,
        String deliveryComment,
        @NotEmpty List<@Valid OrderLineDto> items) {
}
