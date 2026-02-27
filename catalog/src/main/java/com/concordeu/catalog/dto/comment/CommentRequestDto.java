package com.ganchevdimitarg.catalog.dto.comment;

public record CommentRequestDto(
        String title,
        String text,
        double star,
        String author) {
}
