package com.ganchevdimitarg.catalog.dto.product;

import com.ganchevdimitarg.catalog.exception.ValidationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateProductCommand(
        @NotBlank @Size(min = 10, max = 50) String description,
        @NotNull BigDecimal price,
        boolean inStock,
        String characteristics) {

    public UpdateProductCommand {
        if (price != null && price.signum() < 0) {
            throw new ValidationException("price must not be negative");
        }
    }
}
