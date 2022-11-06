package com.concordeu.catalog.dto.product;

import com.concordeu.catalog.dto.category.CategoryResponseDto;
import com.concordeu.catalog.dto.comment.CommentResponseDto;

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