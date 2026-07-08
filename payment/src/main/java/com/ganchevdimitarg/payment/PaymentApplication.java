package com.ganchevdimitarg.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.reactive.ReactiveOAuth2ResourceServerAutoConfiguration;

/**
 * {@code ReactiveOAuth2ResourceServerAutoConfiguration} is gated only on {@code Mono}
 * and {@code BearerTokenAuthenticationToken} being on the classpath, not on the web
 * application type; {@code reactor-core} is pulled in transitively (resilience4j), so
 * without this exclusion Boot instantiates a reactive opaque-token introspector bean
 * that fails with {@code NoClassDefFoundError} on {@code BodyInserter} in this
 * WebMVC-only (never WebFlux) service.
 */
@SpringBootApplication(exclude = ReactiveOAuth2ResourceServerAutoConfiguration.class)
public class PaymentApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}
