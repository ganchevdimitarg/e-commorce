package com.concordeu.profile.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

//@Configuration
public class WebClientConfig implements WebClientCustomizer {
    private final ReactiveOAuth2AuthorizedClientManager authorizedClientManager;
    private final String defaultClientRegistrationId;

    public WebClientConfig(ReactiveOAuth2AuthorizedClientManager authorizedClientManager,
                           @Value("${webClient.oath2Client.defaultClientRegistrationId}") String defaultClientRegistrationId) {
        this.authorizedClientManager = authorizedClientManager;
        this.defaultClientRegistrationId = defaultClientRegistrationId;
    }

    @Override
    public void customize(WebClient.Builder webClientBuilder) {
        ServerOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
                new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
        oauth2Client.setDefaultClientRegistrationId(defaultClientRegistrationId);

        webClientBuilder.filter(oauth2Client);
    }
}
