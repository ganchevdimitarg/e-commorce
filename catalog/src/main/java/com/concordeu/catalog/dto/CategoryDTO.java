package com.concordeu.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
@Builder
public record CategoryDTO(
        UUID id,
        @Size(max = 200)
        @NotBlank
        String name,
        OffsetDateTime createOn,
        OffsetDateTime updateOn,
        Set<ProductDTO> products) {
}
