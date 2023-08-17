package com.concordeu.gateway.controller;

import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

//@RestController
public class FallbackController {

    @GetMapping("/fallback")
    private Mono<String> fallbackGet() {
        return Mono.just("Oops... Something went wrong, please try again later :)");
    }

    @PostMapping("/fallback")
    private Mono<String> fallbackPost() {
        return Mono.just("Oops... Something went wrong, please try again later :)");
    }

    @PutMapping("/fallback")
    private Mono<String> fallbackPut() {
        return Mono.just("Oops... Something went wrong, please try again later :)");
    }

    @DeleteMapping("/fallback")
    private Mono<String> fallbackDelete() {
        return Mono.just("Oops... Something went wrong, please try again later :)");
    }
}

