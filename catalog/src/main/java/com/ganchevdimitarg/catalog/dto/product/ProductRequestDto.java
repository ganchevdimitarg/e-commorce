package com.ganchevdimitarg.catalog.dto.product;

import java.math.BigDecimal;
public record ProductRequestDto(
        String name,
        String description,
        BigDecimal price,
        boolean inStock,
        String characteristics) {
}
