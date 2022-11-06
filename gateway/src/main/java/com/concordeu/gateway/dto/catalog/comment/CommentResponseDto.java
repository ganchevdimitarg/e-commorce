package com.concordeu.gateway.dto.catalog.comment;

import com.concordeu.gateway.dto.catalog.product.ProductRequestDto;

public record CommentResponseDto(
        String title,
        String text,
        double star,
        String author,
        ProductRequestDto product) {
}
