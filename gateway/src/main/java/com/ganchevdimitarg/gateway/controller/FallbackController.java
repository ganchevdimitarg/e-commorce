package com.ganchevdimitarg.gateway.controller;

import com.ganchevdimitarg.gateway.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping
    public Mono<ResponseEntity<ErrorResponse>> generalFallback(ServerWebExchange exchange) {
        return createFallbackResponse(exchange, "The service is temporarily unavailable. Please try again later.");
    }

    @GetMapping("/catalog")
    public Mono<ResponseEntity<ErrorResponse>> catalogFallback(ServerWebExchange exchange) {
        return createFallbackResponse(exchange, "Catalog service is temporarily unavailable. Please try again later.");
    }

    @GetMapping("/profile")
    public Mono<ResponseEntity<ErrorResponse>> profileFallback(ServerWebExchange exchange) {
        return createFallbackResponse(exchange, "Profile service is temporarily unavailable. Please try again later.");
    }

    @GetMapping("/notification")
    public Mono<ResponseEntity<ErrorResponse>> notificationFallback(ServerWebExchange exchange) {
        return createFallbackResponse(exchange, "Notification service is temporarily unavailable. Please try again later.");
    }

    @GetMapping("/order")
    public Mono<ResponseEntity<ErrorResponse>> orderFallback(ServerWebExchange exchange) {
        return createFallbackResponse(exchange, "Order service is temporarily unavailable. Please try again later.");
    }

    @GetMapping("/payment")
    public Mono<ResponseEntity<ErrorResponse>> paymentFallback(ServerWebExchange exchange) {
        return createFallbackResponse(exchange, "Payment service is temporarily unavailable. Please try again later.");
    }

    /**
     * Handles fallback responses for unavailable services
     */
    private Mono<ResponseEntity<ErrorResponse>> createFallbackResponse(
            ServerWebExchange exchange,
            String message
    ) {
        String path = exchange.getRequest().getURI().getPath();
        String method = exchange.getRequest().getMethod().name();
        // Builds error response with details

        log.warn("Circuit breaker triggered: {} {} - Reason: Service unavailable", method, path);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("Service Unavailable")
                .message(message)
                .path(path)
                .method(method)
                .build();

        return Mono.just(ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(errorResponse));
    }
}