package com.ganchevdimitarg.auth.event;

import java.time.Instant;

/**
 * Emitted to the outbox when a password reset is requested for a known account. Carries the
 * RAW reset token so the notification service can email a reset link; the token is single-use
 * and expires at {@code expiresAt}. This payload is written to the outbox only — never logged.
 */
public record PasswordResetRequestedEvent(
        String userId,
        String email,
        String rawToken,
        Instant expiresAt,
        Instant occurredAt) {}
