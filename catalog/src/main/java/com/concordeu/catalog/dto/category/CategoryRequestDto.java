package com.concordeu.catalog.dto.category;

import jakarta.validation.constraints.NotEmpty;

public record CategoryRequestDto(
        @NotEmpty(message = "Category name is empty")
        String name) {
}
