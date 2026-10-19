package com.ganchevdimitarg.catalog.dto.comment;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentCommand(
        @NotBlank @Size(min = 3, max = 15) String title,
        @NotBlank @Size(min = 10, max = 150) String text,
        @DecimalMin(value = "1.0", message = "The star rating must be at least 1")
        @DecimalMax(value = "5.0", message = "The star rating must be at most 5")
        double star,
        @NotBlank String author,
        @NotBlank String productName) {
}
