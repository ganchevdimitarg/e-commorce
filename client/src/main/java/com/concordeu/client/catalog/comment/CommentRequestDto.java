package com.concordeu.client.catalog.comment;

public record CommentRequestDto(
        String title,
        String text,
        double star,
        String author) {
}
