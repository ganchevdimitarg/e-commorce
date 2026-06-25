package com.concordeu.catalog.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryCommand(@NotBlank @Size(min = 2, max = 200) String name) {
}
