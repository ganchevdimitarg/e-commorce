package com.ganchevdimitarg.catalog.dto.product;

import com.ganchevdimitarg.catalog.exception.ValidationException;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateProductCommand(
        @NotBlank @Size(min = 3, max = 200) String name,
        @NotBlank @Size(min = 10, max = 2000) String description,
        @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal price,
        boolean inStock,
        String characteristics,
        @NotBlank String categoryName) {

    public CreateProductCommand {
        if (price != null && price.signum() < 0) {
            throw new ValidationException("price must not be negative");
        }
    }
}
