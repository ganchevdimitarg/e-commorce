package com.ganchevdimitarg.profile.event;

/**
 * Inbound event consumed from the {@code auth.user.deleted} topic.
 */
public record UserDeletedEvent(String userId, String occurredAt) {
}
