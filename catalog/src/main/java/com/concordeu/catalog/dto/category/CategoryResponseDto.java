package com.concordeu.catalog.dto.category;

import com.concordeu.catalog.dto.product.ProductRequestDto;

import java.util.List;
import java.util.UUID;

public record CategoryResponseDto(
        UUID id,
        String name,
        List<ProductRequestDto> products) {
}
