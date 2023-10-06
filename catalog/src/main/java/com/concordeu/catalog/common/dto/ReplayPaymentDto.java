package com.concordeu.catalog.common.dto;

import lombok.Builder;

import java.util.Set;

@Builder
public record ReplayPaymentDto(
        Set<String> cards
) {}
