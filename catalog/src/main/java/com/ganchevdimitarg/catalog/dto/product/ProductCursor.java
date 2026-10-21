package com.ganchevdimitarg.catalog.dto.product;

import java.time.Instant;
import java.util.UUID;

/**
 * Decoded keyset position: the {@code (createdAt, id)} of the last row on the previous page,
 * plus the direction and a fingerprint of the filters that produced it. The version allows
 * the token format to evolve without silently accepting stale cursors.
 */
public record ProductCursor(int version, String direction, Instant createdAt, UUID id, String filterHash) {

    public static final int CURRENT_VERSION = 1;
}
