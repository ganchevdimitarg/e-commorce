package com.ganchevdimitarg.auth.domain;

public record Address(
        String id,
        String city,
        String street,
        String postCode) {
}
