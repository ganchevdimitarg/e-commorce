package com.concordeu.catalog.dto.comment;

import com.concordeu.catalog.dto.product.ProductRequestDto;

public record CommentResponseDto(
        String title,
        String text,
        double star,
        String author,
        ProductRequestDto product) {
}
