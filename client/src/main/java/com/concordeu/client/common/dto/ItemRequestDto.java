package com.concordeu.client.common.dto;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record ItemRequestDto(
        List<UUID> items) {
}
