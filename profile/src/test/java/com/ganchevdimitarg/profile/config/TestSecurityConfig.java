package com.ganchevdimitarg.profile.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    public ReactiveClientRegistrationRepository reactiveClientRegistrationRepository() {
        ClientRegistration registration = ClientRegistration
                .withRegistrationId("test-client")
                .clientId("test")
                .clientSecret("test")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri("http://localhost/oauth2/token")
                .build();
        return new InMemoryReactiveClientRegistrationRepository(registration);
    }
}