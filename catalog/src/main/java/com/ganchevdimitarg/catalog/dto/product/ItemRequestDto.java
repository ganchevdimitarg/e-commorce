package com.ganchevdimitarg.catalog.dto.product;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record ItemRequestDto(
        @NotEmpty(message = "items must not be empty")
        @Size(max = 100, message = "items must contain at most 100 ids")
        List<@NotNull UUID> items) {
}
