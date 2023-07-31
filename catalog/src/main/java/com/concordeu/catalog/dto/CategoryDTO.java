package com.concordeu.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
@Builder
public record CategoryDTO(
        UUID id,
        @Size(max = 200)
        @NotBlank
        String name,
        LocalDateTime createOn,
        LocalDateTime updateOn,
        Set<ProductDTO> products) {
}
