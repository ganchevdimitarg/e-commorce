package com.concordeu.gateway.dto.catalog.product;

import com.concordeu.gateway.dto.catalog.category.CategoryResponseDto;
import com.concordeu.gateway.dto.catalog.comment.CommentResponseDto;

import java.math.BigDecimal;
import java.util.List;

public record ProductResponseDto(
        String id,
        String name,
        String description,
        BigDecimal price,
        boolean inStock,
        String characteristics,
        CategoryResponseDto category,
        List<CommentResponseDto> comments) {
}