package com.ganchevdimitarg.catalog.dto.comment;

import com.ganchevdimitarg.catalog.dto.product.ProductRequestDto;

public record CommentResponseDto(
        String title,
        String text,
        double star,
        String author,
        ProductRequestDto product) {
}
