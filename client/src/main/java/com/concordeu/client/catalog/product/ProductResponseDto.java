package com.concordeu.client.catalog.product;

import java.math.BigDecimal;

public record ProductResponseDto (
        String name,
        String description,
        BigDecimal price,
        boolean inStock,
        String characteristics) {
}