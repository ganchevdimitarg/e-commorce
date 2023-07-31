package com.concordeu.catalog.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record ItemRequestDTO(List<String> items) {
}