package com.concordeu.profile.config;

import com.google.gson.Gson;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

@Configuration
public class ProfileApplicationConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public Gson gson() {
        return new Gson();
    }

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> profileServiceCustomizer() {
        return factory -> factory.configure(builder -> builder
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(2)).build())
                .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults()), "profile-service");
    }
}
