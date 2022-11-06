package com.concordeu.gateway.dto.catalog.category;

import com.concordeu.gateway.dto.catalog.product.ProductRequestDto;

import java.util.List;

public record CategoryResponseDto(
        String id,
        String name,
        List<ProductRequestDto> products) {
}
