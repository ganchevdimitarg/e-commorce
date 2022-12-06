package com.concordeu.catalog.dto.comment;

public record CommentRequestDto(
        String title,
        String text,
        double star,
        String author) {
}
