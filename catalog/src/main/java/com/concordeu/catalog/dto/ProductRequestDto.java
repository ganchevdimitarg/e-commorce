package com.concordeu.catalog.dto;

import java.math.BigDecimal;
public record ProductRequestDto(
        String name,
        String description,
        BigDecimal price,
        boolean inStock,
        String characteristics) {
}
