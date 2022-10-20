package com.concordeu.client.catalog.comment;

public record CommentResponseDto(
        String title,
        String text,
        double star,
        String author) {
}
