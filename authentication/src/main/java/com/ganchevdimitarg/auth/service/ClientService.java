package com.ganchevdimitarg.auth.service;

import com.ganchevdimitarg.auth.dao.ClientRepository;
import com.ganchevdimitarg.auth.domain.*;
import com.ganchevdimitarg.auth.exception.ClientConfigurationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ClientService implements RegisteredClientRepository {

    private final ClientRepository clientRepository;

    @Override
    public void save(@NonNull RegisteredClient registeredClient) {
        clientRepository.save(getClient(registeredClient));
    }

    @Override
    public RegisteredClient findById(@NonNull String id) {
        UUID clientId = parseUuidOrNull(id);
        if (clientId == null) {
            return null;
        }
        return clientRepository.findById(clientId)
                .map(this::getRegisteredClient)
                .orElse(null);
    }

    /**
     * Parses an id into a UUID, returning null for malformed input so callers honour the
     * RegisteredClientRepository contract (null on miss) rather than throwing.
     */
    private UUID parseUuidOrNull(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @Override
    public RegisteredClient findByClientId(@NonNull String clientId) {
        return clientRepository.findByClientId(clientId)
                .map(this::getRegisteredClient)
                .orElse(null);
    }

    /**
     * Converts a client to a registered client with settings
     */
    private RegisteredClient getRegisteredClient(Client client) {
        Consumer<Set<AuthorizationGrantType>> authorizationGrantTypesConsumer = authGrantType -> client.getGrantType()
                .forEach(grantType -> authGrantType.add(new AuthorizationGrantType(grantType.getGrantType())));
        Consumer<Set<String>> scopesConsumer = scope -> client.getScope()
                .forEach(s -> scope.add(s.getScopeName()));
        Consumer<Set<String>> redirectUrisConsumer = redirectUri -> client.getRedirectUri()
                .forEach(r -> redirectUri.add(r.getRedirectUri()));
        TokenSetting tokenSettings = client.getTokenSettings().stream()
                .findFirst()
                .orElseThrow(() -> new ClientConfigurationException(
                        "client %s has no token settings".formatted(client.getId())));

        // Configures a client with ID, secret, and authentication method
        return RegisteredClient.withId(client.getId().toString())
                .clientId(client.getClientId())
                .clientSecret(client.getClientSecret())
                .clientAuthenticationMethod(new ClientAuthenticationMethod(client.getAuthMethod()))
                .authorizationGrantTypes(authorizationGrantTypesConsumer)
                .scopes(scopesConsumer)
                .redirectUris(redirectUrisConsumer)
                // Configures access and refresh token TTLs
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofSeconds(tokenSettings.getAccessTokenTimeToLive()))
                        .refreshTokenTimeToLive(Duration.ofSeconds(tokenSettings.getRefreshTokenTimeToLive()))
                        .build())
                .build();
    }

    private Client getClient(RegisteredClient registeredClient) {
        // Configures a client with ID, secret, and authentication method
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
        // Builds token setting from provided TTLs
        return Set.of(TokenSetting.builder()
                .accessTokenTimeToLive(tokenSettings.getAccessTokenTimeToLive().toSeconds())
                .refreshTokenTimeToLive(tokenSettings.getRefreshTokenTimeToLive().toSeconds())
                .build()
        );
    }

    /**
     * Maps scope strings to scope entities
     */
    private Set<Scope> getScopes(Set<String> scopes) {
        return scopes.stream()
                .map(scope -> Scope.builder()
                        .scopeName(scope)
                        .build()
                )
                .collect(Collectors.toSet());
    }

    private Set<GrantType> getGrantTypes(Set<AuthorizationGrantType> authorizationGrantTypes) {
        // Maps authorization grant types to grant type entities
        return authorizationGrantTypes.stream()
                .map(grantType -> GrantType.builder()
                        .grantType(grantType.getValue())
                        .build()
                )
                .collect(Collectors.toSet());
    }

    private String getAuthMethod(Set<ClientAuthenticationMethod> clientAuthenticationMethods) {
        return clientAuthenticationMethods.stream()
                .findAny()
                .map(ClientAuthenticationMethod::getValue)
                .orElseThrow(() -> new ClientConfigurationException(
                        "registered client has no authentication method"));
    }

    /**
     * Converts strings to redirect URIs for persistence
     */
    private Set<RedirectUri> getRedirectUris(Set<String> redirectUris) {
        return redirectUris.stream()
                .map(r -> RedirectUri.builder()
                        .redirectUri(r)
                        .build()
                )
                .collect(Collectors.toSet());
    }
}
