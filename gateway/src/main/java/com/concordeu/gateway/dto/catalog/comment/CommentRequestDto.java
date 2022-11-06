package com.concordeu.gateway.dto.catalog.comment;

public record CommentRequestDto(
        String title,
        String text,
        double star,
        String author) {
}
