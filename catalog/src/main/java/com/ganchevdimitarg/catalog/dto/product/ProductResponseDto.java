package com.ganchevdimitarg.catalog.dto.product;

import com.ganchevdimitarg.catalog.dto.category.CategorySummaryDto;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponseDto(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        boolean inStock,
        String characteristics,
        CategorySummaryDto category,
        long version,
        double avgStars,
        int ratingCount) {
}
