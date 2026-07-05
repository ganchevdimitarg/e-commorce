package com.ganchevdimitarg.gateway.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.OffsetDateTime;

@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private static final URI PROBLEM_TYPE =
            URI.create("https://api.ecommerce.local/problems/service-unavailable");

    @GetMapping
    public Mono<ResponseEntity<ProblemDetail>> generalFallback(ServerWebExchange exchange) {
        return createFallbackResponse(exchange, "The service is temporarily unavailable. Please try again later.");
    }

    @GetMapping("/catalog")
    public Mono<ResponseEntity<ProblemDetail>> catalogFallback(ServerWebExchange exchange) {
        return createFallbackResponse(exchange, "Catalog service is temporarily unavailable. Please try again later.");
    }

    @GetMapping("/profile")
    public Mono<ResponseEntity<ProblemDetail>> profileFallback(ServerWebExchange exchange) {
        return createFallbackResponse(exchange, "Profile service is temporarily unavailable. Please try again later.");
    }

    @GetMapping("/notification")
    public Mono<ResponseEntity<ProblemDetail>> notificationFallback(ServerWebExchange exchange) {
        return createFallbackResponse(exchange, "Notification service is temporarily unavailable. Please try again later.");
    }

    @GetMapping("/order")
    public Mono<ResponseEntity<ProblemDetail>> orderFallback(ServerWebExchange exchange) {
        return createFallbackResponse(exchange, "Order service is temporarily unavailable. Please try again later.");
    }

    @GetMapping("/payment")
    public Mono<ResponseEntity<ProblemDetail>> paymentFallback(ServerWebExchange exchange) {
        return createFallbackResponse(exchange, "Payment service is temporarily unavailable. Please try again later.");
    }

    /**
     * Builds an RFC 9457 problem+json response for a tripped circuit breaker.
     */
    private Mono<ResponseEntity<ProblemDetail>> createFallbackResponse(
            ServerWebExchange exchange,
            String detail
    ) {
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();

        log.warn("Circuit breaker triggered: {} {} - service unavailable", method, path);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, detail);
        problem.setTitle("Service Unavailable");
        problem.setType(PROBLEM_TYPE);
        problem.setProperty("path", path);
        problem.setProperty("method", method);
        problem.setProperty("timestamp", OffsetDateTime.now());

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem));
    }
}
