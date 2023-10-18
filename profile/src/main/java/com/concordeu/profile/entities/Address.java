package com.concordeu.profile.entities;

import lombok.Builder;

@Builder
public record Address(
        String city,
        String street,
        String postCode) {
}
