package com.ganchevdimitarg.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * WebMVC outbound HTTP: blocking {@link RestClient} carrying an OAuth2 client-credentials
 * bearer token. Replaces the reactive {@code WebClient} setup — reactive types belong only
 * in {@code gateway}. The builder is {@link LoadBalanced} so {@code lb://<service-id>} URIs
 * resolve against Eureka via Spring Cloud LoadBalancer's blocking client — this is the
 * WebMVC-side equivalent of gateway's reactive {@code lb://} route filter, not the same
 * mechanism.
 */
@Configuration
public class RestClientConfig {

    @Value("${webClient.oath2Client.defaultClientRegistrationId}")
    private String defaultClientRegistrationId;

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {

        OAuth2AuthorizedClientProvider authorizedClientProvider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .build();

        AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository, authorizedClientService);
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return authorizedClientManager;
    }

    @Bean
    @LoadBalanced
    RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    RestClient restClient(OAuth2AuthorizedClientManager authorizedClientManager,
                           @LoadBalanced RestClient.Builder loadBalancedBuilder) {
        OAuth2ClientHttpRequestInterceptor requestInterceptor =
                new OAuth2ClientHttpRequestInterceptor(authorizedClientManager);
        requestInterceptor.setClientRegistrationIdResolver(request -> defaultClientRegistrationId);

        // Explicit timeouts so a slow downstream cannot pin the calling thread — the
        // circuit breaker records the timeout instead of waiting indefinitely.
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        return loadBalancedBuilder
                .requestFactory(requestFactory)
                .requestInterceptor(requestInterceptor)
                .build();
    }
}
