package com.concordeu.auth.entities;

import lombok.Builder;

import java.util.UUID;

@Builder
public record GrantType(
        UUID id,
        String grantType
) {
}