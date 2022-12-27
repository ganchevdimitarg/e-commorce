package com.concordeu.gateway.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Collection;

@RestController
public class FallbackController {

    @GetMapping("/google")
    public Collection<? extends GrantedAuthority> getGoogle() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication.getAuthorities();
    }

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
}
