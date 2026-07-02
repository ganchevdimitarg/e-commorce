package com.ganchevdimitarg.auth.event;

import java.time.Instant;

public record UserDeletedEvent(String userId, Instant occurredAt) {}
