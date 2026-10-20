package com.ganchevdimitarg.catalog.dto.comment;

import java.util.UUID;

public record CommentResponseDto(
        UUID id,
        String title,
        String text,
        double star,
        String author,
        UUID productId,
        String productName) {
}
