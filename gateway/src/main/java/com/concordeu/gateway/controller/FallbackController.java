
package com.concordeu.gateway.controller;

import com.concordeu.gateway.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @GetMapping
    public ResponseEntity<ErrorResponse> generalFallback(HttpServletRequest request) {
        return createFallbackResponse(
                request,
                "The service is temporarily unavailable. Please try again later."
        );
    }

    @GetMapping("/catalog")
    public ResponseEntity<ErrorResponse> catalogFallback(HttpServletRequest request) {
        return createFallbackResponse(
                request,
                "Catalog service is temporarily unavailable. Please try again later."
        );
    }

    @GetMapping("/profile")
    public ResponseEntity<ErrorResponse> profileFallback(HttpServletRequest request) {
        return createFallbackResponse(
                request,
                "Profile service is temporarily unavailable. Please try again later."
        );
    }

    @GetMapping("/notification")
    public ResponseEntity<ErrorResponse> notificationFallback(HttpServletRequest request) {
        return createFallbackResponse(
                request,
                "Notification service is temporarily unavailable. Please try again later."
        );
    }

    @GetMapping("/order")
    public ResponseEntity<ErrorResponse> orderFallback(HttpServletRequest request) {
        return createFallbackResponse(
                request,
                "Order service is temporarily unavailable. Please try again later."
        );
    }

    @GetMapping("/payment")
    public ResponseEntity<ErrorResponse> paymentFallback(HttpServletRequest request) {
        return createFallbackResponse(
                request,
                "Payment service is temporarily unavailable. Please try again later."
        );
    }

    private ResponseEntity<ErrorResponse> createFallbackResponse(
            HttpServletRequest request,
            String message
    ) {
        String requestedUri = request.getRequestURI();
        String method = request.getMethod();

        log.warn("Circuit breaker triggered: {} {} - Reason: Service unavailable",
                method, requestedUri);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error("Service Unavailable")
                .message(message)
                .path(requestedUri)
                .method(method)
                .build();

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(errorResponse);
    }


}