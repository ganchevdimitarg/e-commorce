package com.concordeu.catalog.dto;

import com.concordeu.catalog.entities.Product;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import org.hibernate.validator.constraints.Length;

import java.time.OffsetDateTime;
import java.util.UUID;
@Builder
public record CommentDTO(
        UUID id,
        @Length(min = 3, max = 15)
        @NotBlank
        @NotNull
        String title,
        @Length(min = 10, max = 150)
        @NotBlank
        @NotNull
        String text,
        @NotNull
        Double star,
        @Size(max = 200)
        String author,
        OffsetDateTime createOn,
        OffsetDateTime updateOn,
        Product product) {
}
