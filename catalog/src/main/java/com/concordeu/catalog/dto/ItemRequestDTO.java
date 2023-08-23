package com.concordeu.catalog.dto;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record ItemRequestDTO(List<UUID> items) {
}