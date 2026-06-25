package com.concordeu.catalog.dto.product;

import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record ItemRequestDto(
        @NotEmpty(message = "items must not be empty")
        List<UUID> items) {
}
