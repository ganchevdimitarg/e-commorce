package com.concordeu.client.catalog.product;

import com.concordeu.client.catalog.category.CategoryResponseDto;
import com.concordeu.client.catalog.comment.CommentResponseDto;

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