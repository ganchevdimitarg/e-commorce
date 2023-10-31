package com.concordeu.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.publisher.Hooks;

@SpringBootApplication

public class AuthenticationApplication {
    public static void main(String[] args) {
        Hooks.enableAutomaticContextPropagation();
        SpringApplication.run(AuthenticationApplication.class, args);
    }}
