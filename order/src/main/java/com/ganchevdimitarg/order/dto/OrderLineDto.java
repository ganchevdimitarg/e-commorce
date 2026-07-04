package com.ganchevdimitarg.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record OrderLineDto(
        @NotBlank String productId,
        @Positive long quantity) {
}
