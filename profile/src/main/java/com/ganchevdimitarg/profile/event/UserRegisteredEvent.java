package com.ganchevdimitarg.profile.event;

import java.util.Set;

/**
 * Inbound event consumed from the {@code auth.user.registered} topic.
 *
 * <p>Tolerant carrier — extra or missing fields are accepted (the consumer's
 * {@code ObjectMapper} ignores unknown properties), so the auth side may add
 * fields without breaking profile.
 */
public record UserRegisteredEvent(
        String userId, String email, Set<String> roles,
        String firstName, String lastName,
        String phoneNumber, String city, String street, String postCode,
        String occurredAt) {
}
