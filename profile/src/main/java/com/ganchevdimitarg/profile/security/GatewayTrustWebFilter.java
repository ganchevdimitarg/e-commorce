package com.ganchevdimitarg.profile.security;

import com.ganchevdimitarg.client.security.GatewaySignatureVerifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Reactive counterpart to {@code client}'s {@code GatewayTrustFilter} — profile is
 * the platform's one WebFlux business service, so it needs its own {@link WebFilter}
 * rather than the shared servlet {@code OncePerRequestFilter}. Delegates to the same
 * {@link GatewaySignatureVerifier} used by every servlet-based service.
 */
public class GatewayTrustWebFilter implements WebFilter {

    private static final String USER_ID = "X-User-Id";
    private static final String USER_ROLES = "X-User-Roles";
    private static final String TIMESTAMP = "X-Gateway-Timestamp";
    private static final String SIGNATURE = "X-Gateway-Signature";

    private final GatewaySignatureVerifier verifier;

    public GatewayTrustWebFilter(GatewaySignatureVerifier verifier) {
        this.verifier = verifier;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String userId = request.getHeaders().getFirst(USER_ID);
        if (userId == null || userId.isBlank()) {
            return chain.filter(exchange);
        }

        String roles = request.getHeaders().getFirst(USER_ROLES);
        String timestamp = request.getHeaders().getFirst(TIMESTAMP);
        String signature = request.getHeaders().getFirst(SIGNATURE);

        if (!verifier.isValid(userId, roles, timestamp, signature)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            exchange.getResponse().getHeaders().add("Content-Type", "application/problem+json");
            byte[] body = """
                    {"type":"about:blank","title":"Unauthorized","status":401,\
                    "detail":"Missing or invalid gateway trust signature"}"""
                    .getBytes(StandardCharsets.UTF_8);
            return exchange.getResponse().writeWith(
                    Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        }

        return chain.filter(exchange);
    }
}
