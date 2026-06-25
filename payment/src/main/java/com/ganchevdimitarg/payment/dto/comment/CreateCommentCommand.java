package com.concordeu.catalog.dto.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentCommand(
        @NotBlank @Size(min = 3, max = 15) String title,
        @NotBlank @Size(min = 10, max = 150) String text,
        double star,
        @NotBlank String author,
        @NotBlank String productName) {
}
