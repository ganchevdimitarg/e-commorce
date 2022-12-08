package com.concordeu.order.dto;

import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record ProductResponseDto(
        String id,
        String name,
        String description,
        BigDecimal price,
        boolean inStock,
        String characteristics) {
}