package com.concordeu.catalog.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("unit")
class ResilientOpaqueTokenIntrospectorTest {

    @Test
    void should_delegate_when_circuitClosed() {
        OpaqueTokenIntrospector delegate = mock(OpaqueTokenIntrospector.class);
        OAuth2AuthenticatedPrincipal principal = mock(OAuth2AuthenticatedPrincipal.class);
        when(delegate.introspect("token")).thenReturn(principal);

        ResilientOpaqueTokenIntrospector introspector =
                new ResilientOpaqueTokenIntrospector(delegate, CircuitBreakerRegistry.ofDefaults());

        assertThat(introspector.introspect("token")).isSameAs(principal);
    }
}
