package com.ganchevdimitarg.catalog.dto.product;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequestDto(
        @NotBlank @Size(min = 3, max = 200, message = "The name is not correct!") String name,
        @NotBlank @Size(min = 10, max = 2000, message = "The description is not correct!") String description,
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2, message = "The price is not correct!")
        BigDecimal price,
        boolean inStock,
        String characteristics) {
}
