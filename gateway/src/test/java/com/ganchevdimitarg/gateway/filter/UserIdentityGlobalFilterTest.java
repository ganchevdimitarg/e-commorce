package com.ganchevdimitarg.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UserIdentityGlobalFilterTest {

    private final UserIdentityGlobalFilter filter = new UserIdentityGlobalFilter();

    @Test
    void should_stripSpoofedIdentityHeaders_when_requestIsUnauthenticated() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/catalog/items")
                        .header(UserIdentityGlobalFilter.USER_ID, "attacker")
                        .header(UserIdentityGlobalFilter.USER_ROLES, "ROLE_ADMIN"));

        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            forwarded.set(ex.getRequest());
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        HttpHeaders headers = forwarded.get().getHeaders();
        assertThat(headers.getFirst(UserIdentityGlobalFilter.USER_ID)).isNull();
        assertThat(headers.getFirst(UserIdentityGlobalFilter.USER_ROLES)).isNull();
    }

    @Test
    void should_overwriteSpoofedHeadersWithPrincipal_when_authenticated() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/order")
                        .header(UserIdentityGlobalFilter.USER_ID, "attacker"));

        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            forwarded.set(ex.getRequest());
            return Mono.empty();
        };

        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user-123", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));

        StepVerifier.create(filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .verifyComplete();

        HttpHeaders headers = forwarded.get().getHeaders();
        assertThat(headers.getFirst(UserIdentityGlobalFilter.USER_ID)).isEqualTo("user-123");
        assertThat(headers.getFirst(UserIdentityGlobalFilter.USER_ROLES)).isEqualTo("ROLE_USER");
    }

    @Test
    void should_stripHeaders_when_authenticationIsAnonymous() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/catalog/items")
                        .header(UserIdentityGlobalFilter.USER_ID, "attacker"));

        AtomicReference<ServerHttpRequest> forwarded = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            forwarded.set(ex.getRequest());
            return Mono.empty();
        };

        Authentication anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        StepVerifier.create(filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(anonymous)))
                .verifyComplete();

        assertThat(forwarded.get().getHeaders().getFirst(UserIdentityGlobalFilter.USER_ID)).isNull();
    }
}
