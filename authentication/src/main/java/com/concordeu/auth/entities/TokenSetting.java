package com.concordeu.auth.entities;

import lombok.Builder;

import java.util.UUID;

@Builder
public record TokenSetting(
        UUID id,
        Long accessTokenTimeToLive,
        Long refreshTokenTimeToLive
) {
}