package com.ganchevdimitarg.client.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rejects any request that claims an {@code X-User-Id} without a valid,
 * fresh gateway signature. A request with no {@code X-User-Id} at all makes
 * no identity claim and passes through unchanged — this keeps existing
 * service-to-service calls that never set the header (e.g. a plain username
 * lookup) working.
 */
public final class GatewayTrustFilter extends OncePerRequestFilter {

    /** Header name for user identifier. */
    private static final String USER_ID = "X-User-Id";

    /** Header name for user roles. */
    private static final String USER_ROLES = "X-User-Roles";

    /** Header name for gateway timestamp. */
    private static final String TIMESTAMP = "X-Gateway-Timestamp";

    /** Header name for gateway signature. */
    private static final String SIGNATURE = "X-Gateway-Signature";

    /** Verifies gateway signatures. */
    private final GatewaySignatureVerifier signatureVerifier;

    /**
     * Constructs a GatewayTrustFilter with a signature verifier.
     *
     * @param verifier the signature verifier to use
     */
    public GatewayTrustFilter(final GatewaySignatureVerifier verifier) {
        this.signatureVerifier = verifier;
    }

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain chain)
            throws ServletException, IOException {
        String userId = request.getHeader(USER_ID);
        if (userId == null || userId.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        String roles = request.getHeader(USER_ROLES);
        String timestamp = request.getHeader(TIMESTAMP);
        String signature = request.getHeader(SIGNATURE);

        if (!signatureVerifier.isValid(userId, roles, timestamp, signature)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/problem+json");
            response.getWriter().write("""
                    {"type":"about:blank","title":"Unauthorized","status":401,\
                    "detail":"Missing or invalid gateway trust signature"}""");
            return;
        }

        chain.doFilter(request, response);
    }
}
