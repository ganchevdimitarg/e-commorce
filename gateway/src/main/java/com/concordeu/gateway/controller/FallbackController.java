package com.concordeu.gateway.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class FallbackController {
    @GetMapping("/catalog-fallback")
    Mono<String> getCategoryFallback() {
        return Mono.just("Oops... Something went wrong, please try again later :)");
    }
    @GetMapping("/order-fallback")
    Mono<String> getOrderFallback() {
        return Mono.just("Oops... Something went wrong, please try again later :)");
    }
    @GetMapping("/profile-fallback")
    Mono<String> getProfileFallback() {
        return Mono.just("Oops... Something went wrong, please try again later :)");
    }
    @GetMapping("/notification-fallback")
    Mono<String> getNotificationFallback() {
        return Mono.just("Oops... Something went wrong, please try again later :)");
    }
    @GetMapping("/payment-fallback")
    Mono<String> getPaymentFallback() {
        return Mono.just("Oops... Something went wrong, please try again later :)");
    }
}
