package com.concordeu.catalog.dto;

import com.concordeu.catalog.entities.Category;
import com.concordeu.catalog.entities.Comment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Builder
public record ProductDTO(
        UUID id,
        @NotBlank
        @NotNull
        @Size(min = 3, max = 20)
        String name,
        @NotBlank
        @NotNull
        @Size(min = 10, max = 50)
        String description,
        @NotNull
        BigDecimal price,
        boolean inStock,
        String characteristics,
        LocalDateTime createOn,
        LocalDateTime updateOn,
        Category category,
        Set<Comment> comments) {
}
