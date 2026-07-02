package com.ganchevdimitarg.auth.event;

import java.time.Instant;
import java.util.Set;

public record UserRegisteredEvent(
        String userId,
        String email,
        Set<String> roles,
        String firstName,
        String lastName,
        String phoneNumber,
        String city,
        String street,
        String postCode,
        Instant occurredAt) {}
