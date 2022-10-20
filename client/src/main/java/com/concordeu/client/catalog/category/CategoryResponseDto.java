package com.concordeu.client.catalog.category;

import com.concordeu.client.catalog.product.ProductRequestDto;

import java.util.List;

public record CategoryResponseDto(
        String id,
        String name,
        List<ProductRequestDto> products) {
}
