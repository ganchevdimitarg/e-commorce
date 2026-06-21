package com.concordeu.catalog.dto.product;

import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;

import java.util.List;

@Builder
public record ItemRequestDto(
        @NotEmpty(message = "items must not be empty")
        List<String> items) {
}
