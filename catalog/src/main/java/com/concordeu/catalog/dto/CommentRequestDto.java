package com.concordeu.catalog.dto;

public record CommentRequestDto(
        String title,
        String text,
        double star,
        String author) {
}
