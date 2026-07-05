package com.ganchevdimitarg.gateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackControllerTest {

    private final FallbackController controller = new FallbackController();

    @Test
    void should_returnServiceUnavailableProblemJson_when_catalogCircuitIsOpen() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/fallback/catalog"));

        StepVerifier.create(controller.catalogFallback(exchange))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(response.getHeaders().getContentType())
                            .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

                    ProblemDetail body = response.getBody();
                    assertThat(body).isNotNull();
                    assertThat(body.getStatus()).isEqualTo(503);
                    assertThat(body.getTitle()).isEqualTo("Service Unavailable");
                    assertThat(body.getDetail()).contains("Catalog service");
                    assertThat(body.getProperties()).containsKeys("path", "method", "timestamp");
                    assertThat(body.getProperties().get("method")).isEqualTo("GET");
                    assertThat(body.getProperties().get("path")).isEqualTo("/fallback/catalog");
                })
                .verifyComplete();
    }
}
