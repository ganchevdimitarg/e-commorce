package com.ganchevdimitarg.auth.service;

import com.ganchevdimitarg.auth.dao.ClientDao;
import com.ganchevdimitarg.auth.domain.*;
import com.ganchevdimitarg.auth.exception.ClientConfigurationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClientService Unit Tests")
class ClientServiceTest {

    @Mock
    private ClientDao clientDao;

    @InjectMocks
    private ClientService clientService;

    private Client mockClient;
    private RegisteredClient mockRegisteredClient;
    private UUID clientUuid;

    @BeforeEach
    void setUp() {
        clientUuid = UUID.randomUUID();

        TokenSetting tokenSetting = TokenSetting.builder()
                .accessTokenTimeToLive(3600L)
                .refreshTokenTimeToLive(86400L)
                .build();

        GrantType grantType = GrantType.builder()
                .grantType("authorization_code")
                .build();

        Scope scope = Scope.builder()
                .scopeName("read")
                .build();

        RedirectUri redirectUri = RedirectUri.builder()
                .redirectUri("http://localhost:8080/callback")
                .build();

        mockClient = Client.builder()
                .id(clientUuid)
                .clientId("test-client")
                .clientSecret("{noop}secret")
                .authMethod("client_secret_basic")
                .grantType(Set.of(grantType))
                .scope(Set.of(scope))
                .redirectUri(Set.of(redirectUri))
                .tokenSettings(Set.of(tokenSetting))
                .build();

        mockRegisteredClient = RegisteredClient.withId(clientUuid.toString())
                .clientId("test-client")
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .scope("read")
                .redirectUri("http://localhost:8080/callback")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofSeconds(3600))
                        .refreshTokenTimeToLive(Duration.ofSeconds(86400))
                        .build())
                .build();
    }

    @Test
    @DisplayName("save() should convert and save RegisteredClient successfully")
    void should_convertAndSave_when_registeredClientGiven() {
        // Arrange
        when(clientDao.save(any(Client.class))).thenReturn(mockClient);

        // Act
        clientService.save(mockRegisteredClient);

        // Assert
        verify(clientDao, times(1)).save(any(Client.class));
    }

    @Test
    @DisplayName("findById() should return RegisteredClient when client exists")
    void should_returnRegisteredClient_when_findByIdExists() {
        // Arrange
        when(clientDao.findById(any(UUID.class))).thenReturn(Optional.of(mockClient));

        // Act
        RegisteredClient result = clientService.findById(clientUuid.toString());

        // Assert
        assertNotNull(result);
        assertEquals("test-client", result.getClientId());
        assertEquals("{noop}secret", result.getClientSecret());
        assertEquals(clientUuid.toString(), result.getId());
        assertTrue(result.getScopes().contains("read"));
        assertTrue(result.getRedirectUris().contains("http://localhost:8080/callback"));
        assertEquals(Duration.ofSeconds(3600), result.getTokenSettings().getAccessTokenTimeToLive());
        assertEquals(Duration.ofSeconds(86400), result.getTokenSettings().getRefreshTokenTimeToLive());
        verify(clientDao, times(1)).findById(clientUuid);
    }

    @Test
    @DisplayName("findById() returns null when client not found (repository contract)")
    void should_returnNull_when_findByIdMisses() {
        when(clientDao.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertNull(clientService.findById(clientUuid.toString()));
    }

    @Test
    @DisplayName("findByClientId() should return RegisteredClient when client exists")
    void should_returnRegisteredClient_when_findByClientIdExists() {
        // Arrange
        when(clientDao.findByClientId(anyString())).thenReturn(Optional.of(mockClient));

        // Act
        RegisteredClient result = clientService.findByClientId("test-client");

        // Assert
        assertNotNull(result);
        assertEquals("test-client", result.getClientId());
        assertEquals("{noop}secret", result.getClientSecret());
        assertEquals(clientUuid.toString(), result.getId());
        assertTrue(result.getAuthorizationGrantTypes().contains(AuthorizationGrantType.AUTHORIZATION_CODE));
        assertTrue(result.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC));
        verify(clientDao, times(1)).findByClientId("test-client");
    }

    @Test
    @DisplayName("findByClientId() returns null when client not found (repository contract)")
    void should_returnNull_when_findByClientIdMisses() {
        when(clientDao.findByClientId(anyString())).thenReturn(Optional.empty());

        assertNull(clientService.findByClientId("non-existent-client"));
    }

    @Test
    @DisplayName("findById() throws ClientConfigurationException when token settings missing")
    void should_throwClientConfiguration_when_tokenSettingsMissing() {
        mockClient.setTokenSettings(Set.of());
        when(clientDao.findById(any(UUID.class))).thenReturn(Optional.of(mockClient));

        assertThrows(ClientConfigurationException.class,
                () -> clientService.findById(clientUuid.toString()));
    }

    // NOTE: getAuthMethod()'s empty-set guard (ClientConfigurationException) is defensive only —
    // RegisteredClient.build() always populates at least one authentication method, so the empty
    // branch is unreachable through the public save() API and is intentionally left without a test
    // (a reflection-driven test of an unreachable branch would assert against an impossible state).

    @Test
    @DisplayName("findById() returns null when id is malformed (repository contract)")
    void should_returnNull_when_idMalformed() {
        assertNull(clientService.findById("not-a-uuid"));
        verifyNoInteractions(clientDao);
    }

    @Test
    @DisplayName("findById() should handle multiple scopes correctly")
    void should_mapMultipleScopes_when_findById() {
        // Arrange
        Scope scope1 = Scope.builder().scopeName("read").build();
        Scope scope2 = Scope.builder().scopeName("write").build();
        mockClient.setScope(Set.of(scope1, scope2));
        Client clientWithMultipleScopes = mockClient;

        when(clientDao.findById(any(UUID.class))).thenReturn(Optional.of(clientWithMultipleScopes));

        // Act
        RegisteredClient result = clientService.findById(clientUuid.toString());

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getScopes().size());
        assertTrue(result.getScopes().contains("read"));
        assertTrue(result.getScopes().contains("write"));
    }

    @Test
    @DisplayName("findById() should handle multiple grant types correctly")
    void should_mapMultipleGrantTypes_when_findById() {
        // Arrange
        GrantType grantType1 = GrantType.builder().grantType("authorization_code").build();
        GrantType grantType2 = GrantType.builder().grantType("refresh_token").build();
        mockClient.setGrantType(Set.of(grantType1, grantType2));
        Client clientWithMultipleGrants = mockClient;

        when(clientDao.findById(any(UUID.class))).thenReturn(Optional.of(clientWithMultipleGrants));

        // Act
        RegisteredClient result = clientService.findById(clientUuid.toString());

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getAuthorizationGrantTypes().size());
        assertTrue(result.getAuthorizationGrantTypes().contains(AuthorizationGrantType.AUTHORIZATION_CODE));
        assertTrue(result.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN));
    }

    @Test
    @DisplayName("findById() should handle multiple redirect URIs correctly")
    void should_mapMultipleRedirectUris_when_findById() {
        // Arrange
        RedirectUri uri1 = RedirectUri.builder().redirectUri("http://localhost:8080/callback").build();
        RedirectUri uri2 = RedirectUri.builder().redirectUri("http://localhost:8080/callback2").build();
        mockClient.setRedirectUri(Set.of(uri1, uri2));
        Client clientWithMultipleUris = mockClient;

        when(clientDao.findById(any(UUID.class))).thenReturn(Optional.of(clientWithMultipleUris));

        // Act
        RegisteredClient result = clientService.findById(clientUuid.toString());

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getRedirectUris().size());
        assertTrue(result.getRedirectUris().contains("http://localhost:8080/callback"));
        assertTrue(result.getRedirectUris().contains("http://localhost:8080/callback2"));
    }

    @Test
    @DisplayName("save() should convert RegisteredClient with multiple authentication methods")
    void should_convertAndSave_when_multipleAuthMethods() {
        // Arrange
        RegisteredClient clientWithMultipleAuthMethods = RegisteredClient.withId(clientUuid.toString())
                .clientId("test-client")
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .scope("read")
                .redirectUri("http://localhost:8080/callback")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofSeconds(3600))
                        .refreshTokenTimeToLive(Duration.ofSeconds(86400))
                        .build())
                .build();

        when(clientDao.save(any(Client.class))).thenReturn(mockClient);

        // Act
        clientService.save(clientWithMultipleAuthMethods);

        // Assert
        verify(clientDao, times(1)).save(any(Client.class));
    }
}

