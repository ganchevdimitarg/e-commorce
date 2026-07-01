package com.ganchevdimitarg.profile.config;

import com.mongodb.ConnectionString;
import org.springframework.boot.mongodb.autoconfigure.MongoConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

@TestConfiguration
public class TestSecurityConfig {

    /**
     * Provide a {@link MongoConnectionDetails} bean that points directly to the
     * Testcontainer MongoDB instance.  This overrides the auto-configured
     * {@link org.springframework.boot.mongodb.autoconfigure.PropertiesMongoConnectionDetails}
     * which would otherwise fall back to {@code mongodb://localhost/test}.
     */
    @Bean
    @Primary
    public MongoConnectionDetails testMongoConnectionDetails() {
        return () -> new ConnectionString(BaseTest.MONGO.getConnectionString() + "/test");
    }

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

    @Bean
    @Primary
    public ReactiveOAuth2AuthorizedClientService reactiveOAuth2AuthorizedClientService(
            ReactiveClientRegistrationRepository clientRegistrationRepository) {
        return new InMemoryReactiveOAuth2AuthorizedClientService(clientRegistrationRepository);
    }
}