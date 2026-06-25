package com.ganchevdimitarg.catalog.dto.product;

import com.ganchevdimitarg.catalog.dto.category.CategoryResponseDto;
import com.ganchevdimitarg.catalog.dto.comment.CommentResponseDto;

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