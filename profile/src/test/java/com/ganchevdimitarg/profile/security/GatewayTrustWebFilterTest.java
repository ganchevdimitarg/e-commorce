package com.ganchevdimitarg.profile.security;

import com.ganchevdimitarg.client.security.GatewaySignatureVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayTrustWebFilterTest {

    private static final String SECRET = "test-shared-secret";
    private final GatewaySignatureVerifier verifier = new GatewaySignatureVerifier(SECRET);
    private final GatewayTrustWebFilter filter = new GatewayTrustWebFilter(verifier);

    @Test
    void should_passThrough_when_noUserIdHeaderPresent() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/profile/me"));
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chainCalled).isTrue();
    }

    @Test
    void should_allowRequest_when_signatureValidAndFresh() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = verifier.sign("user-1", "ROLE_USER", timestamp);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/profile/me")
                        .header("X-User-Id", "user-1")
                        .header("X-User-Roles", "ROLE_USER")
                        .header("X-Gateway-Timestamp", timestamp)
                        .header("X-Gateway-Signature", signature));
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(chainCalled).isTrue();
    }

    @Test
    void should_reject401_when_signatureMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/profile/me")
                        .header("X-User-Id", "attacker"));
        WebFilterChain chain = ex -> Mono.error(new AssertionError("chain must not run"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
