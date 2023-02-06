package com.concordeu.auth.service;

import com.concordeu.auth.dao.ClientDao;
import com.concordeu.auth.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ClientService implements RegisteredClientRepository {

    private final ClientDao clientDao;

    @Override
    public void save(RegisteredClient registeredClient) {
        clientDao.save(getClient(registeredClient));
    }

    @Override
    public RegisteredClient findById(String id) {
        Client client = clientDao.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No such client"));
        return getRegisteredClient(client);
    }

    @Override
    public RegisteredClient findByClientId(String clientId) {
        Client client = clientDao.findByClientId(clientId)
                .orElseThrow(() -> new IllegalArgumentException("No such client"));
        return getRegisteredClient(client);
    }

    private RegisteredClient getRegisteredClient(Client client) {
        Consumer<Set<AuthorizationGrantType>> authorizationGrantTypesConsumer = authGrantType -> client.getGrantType()
                .forEach(grantType -> authGrantType.add(new AuthorizationGrantType(grantType.getGrantType())));
        Consumer<Set<String>> scopesConsumer = scope -> client.getScope()
                .forEach(s -> scope.add(s.getScope()));
        Consumer<Set<String>> redirectUrisConsumer = redirectUri -> client.getRedirectUri().forEach(r -> redirectUri.add(r.getRedirectUri()));
        TokenSetting tokenSettings = client.getTokenSettings().stream().findFirst().get();

        return RegisteredClient.withId(client.getId().toString())
                .clientId(client.getClientId())
                .clientSecret(client.getClientSecret())
                .clientAuthenticationMethod(new ClientAuthenticationMethod(client.getAuthMethod()))
                .authorizationGrantTypes(authorizationGrantTypesConsumer)
                .scopes(scopesConsumer)
                .redirectUris(redirectUrisConsumer)
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofSeconds(tokenSettings.getAccessTokenTimeToLive()))
                        .refreshTokenTimeToLive(Duration.ofSeconds(tokenSettings.getRefreshTokenTimeToLive()))
                        .build())
                .build();
    }

    private Client getClient(RegisteredClient registeredClient) {
        return Client.builder()
                .clientId(registeredClient.getClientId())
                .clientSecret(registeredClient.getClientSecret())
                .authMethod(getAuthMethod(registeredClient.getClientAuthenticationMethods()))
                .redirectUri(getRedirectUris(registeredClient.getRedirectUris()))
                .grantType(getGrantTypes(registeredClient.getAuthorizationGrantTypes()))
                .scope(getScopes(registeredClient.getScopes()))
                .tokenSettings(getTokenSettings(registeredClient.getTokenSettings()))
                .build();
    }

    private Set<TokenSetting> getTokenSettings(TokenSettings tokenSettings) {
        return Set.of(TokenSetting.builder()
                .accessTokenTimeToLive(tokenSettings.getAccessTokenTimeToLive().toSeconds())
                .refreshTokenTimeToLive(tokenSettings.getRefreshTokenTimeToLive().toSeconds())
                .build()
        );
    }

    private Set<Scope> getScopes(Set<String> scopes) {
        return scopes.stream()
                .map(scope -> Scope.builder()
                        .scope(scope)
                        .build()
                )
                .collect(Collectors.toSet());
    }

    private Set<GrantType> getGrantTypes(Set<AuthorizationGrantType> authorizationGrantTypes) {
        return authorizationGrantTypes.stream()
                .map(grantType -> GrantType.builder()
                        .grantType(grantType.getValue())
                        .build()
                )
                .collect(Collectors.toSet());
    }

    private String getAuthMethod(Set<ClientAuthenticationMethod> clientAuthenticationMethods) {
        return clientAuthenticationMethods.stream().findAny().get().getValue();
    }

    private Set<RedirectUri> getRedirectUris(Set<String> redirectUris) {
        return redirectUris.stream()
                .map(r -> RedirectUri.builder()
                        .redirectUri(r)
                        .build()
                )
                .collect(Collectors.toSet());
    }
}
