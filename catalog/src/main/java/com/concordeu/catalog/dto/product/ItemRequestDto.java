package com.concordeu.catalog.dto.product;

import lombok.Builder;

import java.util.List;

@Builder
public record ItemRequestDto(
        List<String> items) {
}