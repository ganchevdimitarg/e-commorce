package com.ganchevdimitarg.gateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Configuration
public class Resilience4jConfig {

    /**
     * Applies the circuit-breaker and time-limiter defaults to every gateway route.
     *
     * <p>Spring Cloud Gateway's {@code CircuitBreaker} filter is configured through a
     * {@link Customizer} of the {@link ReactiveResilience4JCircuitBreakerFactory}; bare
     * {@code CircuitBreakerConfig} beans are ignored, so this is the only wiring that
     * actually takes effect.
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCircuitBreakerCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .minimumNumberOfCalls(10)
                        .slidingWindowSize(10)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .permittedNumberOfCallsInHalfOpenState(5)
                        .recordExceptions(
                                WebClientResponseException.InternalServerError.class,
                                TimeoutException.class,
                                IOException.class
                        )
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(5))
                        .cancelRunningFuture(true)
                        .build())
                .build());
    }
}
