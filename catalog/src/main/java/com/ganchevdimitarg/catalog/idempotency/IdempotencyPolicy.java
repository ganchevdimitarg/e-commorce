package com.ganchevdimitarg.catalog.idempotency;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/**
 * Decides which requests need idempotency protection. Shared by the MVC interceptor and the
 * response-capture filter so the two never disagree about which requests are guarded.
 */
public final class IdempotencyPolicy {

    public static final Set<String> GUARDED = Set.of("POST", "PUT", "DELETE");

    private static final String BATCH_GET_SUFFIX = ":batch-get";

    private IdempotencyPolicy() {
    }

    public static boolean isGuarded(HttpServletRequest request) {
        String method = request.getMethod();
        // :batch-get is a read expressed as POST; it carries no state change to protect.
        return GUARDED.contains(method)
                && !("POST".equals(method) && request.getRequestURI().endsWith(BATCH_GET_SUFFIX));
    }
}
