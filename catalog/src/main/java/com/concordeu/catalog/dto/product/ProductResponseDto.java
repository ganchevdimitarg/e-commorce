package com.concordeu.catalog.dto.product;

import com.concordeu.catalog.dto.category.CategoryResponseDto;
import com.concordeu.catalog.dto.comment.CommentResponseDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductResponseDto(
        UUID id,
        String name,
        String description,
        BigDecimal price,
        boolean inStock,
        String characteristics,
        CategoryResponseDto category,
        List<CommentResponseDto> comments) {
}