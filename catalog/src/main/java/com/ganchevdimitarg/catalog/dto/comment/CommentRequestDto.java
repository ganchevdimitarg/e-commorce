package com.ganchevdimitarg.catalog.dto.comment;

import jakarta.validation.constraints.Size;

public record CommentRequestDto(
        @Size(min = 3, max = 15, message = "The title is not correct!")
        String title,
        @Size(min = 10, max = 150, message = "The text is not correct!")
        String text,
        double star,
        String author) {
}
