package com.ganchevdimitarg.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * Establishes the trusted identity contract with downstream services.
 *
 * <p>Downstream services trust {@code X-User-Id} / {@code X-User-Roles} without
 * re-validating them, so the gateway must be the sole source of those headers. This
 * filter therefore always <em>strips</em> any client-supplied identity headers (closing
 * the spoofing hole) and, for an authenticated exchange, re-injects them from the
 * OAuth2 principal.
 */
@Component
public class UserIdentityGlobalFilter implements GlobalFilter, Ordered {

    static final String USER_ID = "X-User-Id";
    static final String USER_ROLES = "X-User-Roles";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerWebExchange stripped = exchange.mutate()
                .request(r -> r.headers(h -> {
                    h.remove(USER_ID);
                    h.remove(USER_ROLES);
                }))
                .build();

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth != null && auth.isAuthenticated())
                .filter(auth -> !(auth instanceof AnonymousAuthenticationToken))
                .map(auth -> withIdentityHeaders(stripped, auth))
                .defaultIfEmpty(stripped)
                .flatMap(chain::filter);
    }

    private ServerWebExchange withIdentityHeaders(ServerWebExchange exchange, Authentication auth) {
        String userId = resolveUserId(auth);
        String roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        return exchange.mutate()
                .request(r -> r.headers(h -> {
                    h.set(USER_ID, userId);
                    if (!roles.isBlank()) {
                        h.set(USER_ROLES, roles);
                    }
                }))
                .build();
    }

    private String resolveUserId(Authentication auth) {
        return switch (auth.getPrincipal()) {
            case OidcUser oidc -> oidc.getSubject();
            case OAuth2User oauth2 -> oauth2.getName();
            default -> auth.getName();
        };
    }

    @Override
    public int getOrder() {
        // Run early so downstream-facing filters (e.g. TokenRelay) see the trusted headers.
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
