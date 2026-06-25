package com.concordeu.catalog.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

@Slf4j
public class ResilientOpaqueTokenIntrospector implements OpaqueTokenIntrospector {

    private final OpaqueTokenIntrospector delegate;
    private final CircuitBreaker circuitBreaker;

    public ResilientOpaqueTokenIntrospector(OpaqueTokenIntrospector delegate,
                                            CircuitBreakerRegistry registry) {
        this.delegate = delegate;
        this.circuitBreaker = registry.circuitBreaker("introspection");
    }

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        try {
            return circuitBreaker.executeSupplier(() -> delegate.introspect(token));
        } catch (OAuth2IntrospectionException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Token introspection unavailable (circuit '{}'): {}",
                    circuitBreaker.getName(), e.getMessage());
            throw new OAuth2IntrospectionException("Token introspection temporarily unavailable", e);
        }
    }
}
