package com.concordeu.catalog.dto.category;

import com.concordeu.catalog.dto.product.ProductRequestDto;

import java.util.List;

public record CategoryResponseDto(
        String id,
        String name,
        List<ProductRequestDto> products) {
}
