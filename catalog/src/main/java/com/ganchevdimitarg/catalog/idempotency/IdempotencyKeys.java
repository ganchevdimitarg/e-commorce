package com.ganchevdimitarg.catalog.idempotency;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Builds the Redis key for an idempotent request.
 *
 * <p>The key is scoped by principal, HTTP method, request path and the client-supplied
 * {@code Idempotency-Key}, then hashed with SHA-256 so the key length is bounded and neither the
 * user identifier nor the client key leaks into Redis key listings.
 */
public final class IdempotencyKeys {

    public static final String PREFIX = "catalog:idempotency:";

    private static final String ANONYMOUS = "anonymous";

    private IdempotencyKeys() {
    }

    /**
     * Returns the JWT subject of the current request, or {@code anonymous} when the request is
     * unauthenticated. Read at call time because the security context is populated by the
     * security filter chain, which runs before the MVC interceptor.
     */
    public static String principalName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return ANONYMOUS;
        }
        return authentication.getName();
    }

    public static String scopedKey(String principal, String method, String path, String clientKey) {
        String material = principal + "\n" + method + "\n" + path + "\n" + clientKey;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
            return PREFIX + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform is required to provide SHA-256.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
