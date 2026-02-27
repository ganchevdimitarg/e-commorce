package com.ganchevdimitarg.catalog.dto.category;

import com.ganchevdimitarg.catalog.dto.product.ProductRequestDto;

import java.util.List;

public record CategoryResponseDto(
        String id,
        String name,
        List<ProductRequestDto> products) {
}
