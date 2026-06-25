package com.ganchevdimitarg.catalog.dto.product;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequestDto(
        @Size(min = 3, max = 20, message = "The name is not correct!")
        String name,
        @Size(min = 10, max = 50, message = "The description is not correct!")
        String description,
        @NotNull(message = "The price is not correct!")
        BigDecimal price,
        boolean inStock,
        String characteristics) {
}
