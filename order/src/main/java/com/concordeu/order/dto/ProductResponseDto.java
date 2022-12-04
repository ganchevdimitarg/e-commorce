package com.concordeu.order.dto;

import java.math.BigDecimal;

public record ProductResponseDto(
        String name,
        String description,
        BigDecimal price,
        boolean inStock,
        String characteristics) {
}