package com.concordeu.client.catalog.comment;

import com.concordeu.client.catalog.product.ProductRequestDto;

public record CommentResponseDto(
        String title,
        String text,
        double star,
        String author,
        ProductRequestDto product) {
}
