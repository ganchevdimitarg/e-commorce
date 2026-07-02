package com.ganchevdimitarg.catalog.dto.category;

import jakarta.validation.constraints.NotBlank;

public record MoveProductCommand(
        @NotBlank String categoryNameFrom,
        @NotBlank String categoryNameTo,
        @NotBlank String productName) {
}
