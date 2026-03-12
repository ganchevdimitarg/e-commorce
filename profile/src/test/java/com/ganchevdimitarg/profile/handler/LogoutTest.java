package com.ganchevdimitarg.profile.handler;

import com.ganchevdimitarg.profile.property.EcommerceOAuth2Properties;
import com.ganchevdimitarg.profile.property.GithubProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogoutTest {

    @Mock
    WebClient webClient;
    @Mock
    GithubProperties githubProperties;
    @Mock
    EcommerceOAuth2Properties ecommerceProperties;
    @Mock
    WebFilterExchange webFilterExchange;
    @Mock
    ServerWebExchange serverWebExchange;
    @Mock
    ServerHttpRequest serverHttpRequest;
    @Mock
    HttpHeaders httpHeaders;

    private CustomLogoutHandler logoutHandler;

    /**
     * Sets up mocks and handler with dependencies
     */
    @BeforeEach
    void setUp() {
        logoutHandler = new CustomLogoutHandler(
                webClient, githubProperties, ecommerceProperties);
        ReflectionTestUtils.setField(logoutHandler, "ecommerceRevokeUri",
                "http://localhost:8082/oauth2/revoke");

        when(webFilterExchange.getExchange()).thenReturn(serverWebExchange);
        when(serverWebExchange.getRequest()).thenReturn(serverHttpRequest);
        when(serverHttpRequest.getHeaders()).thenReturn(httpHeaders);
    }

    @Test
    void logout_missingAuthorizationHeader_returnsEmpty() {
        // Arrange
        when(httpHeaders.getFirst("Authorization")).thenReturn(null);

        // Act
        Mono<Void> result = logoutHandler.logout(webFilterExchange, null);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verifyNoInteractions(webClient);
    }

    @Test
    void logout_invalidHeaderFormat_returnsEmpty() {
        // Arrange
        when(httpHeaders.getFirst("Authorization")).thenReturn("Basic sometoken");

        // Act
        Mono<Void> result = logoutHandler.logout(webFilterExchange, null);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verifyNoInteractions(webClient);
    }

    @Test
    void logout_jwtToken_delegatesToEcommerceRevocation() {
        // Arrange — JWT has exactly 2 dots
        String jwtToken = "header.payload.signature";
        when(httpHeaders.getFirst("Authorization")).thenReturn("Bearer " + jwtToken);

        // Mock WebClient chain
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.headers(any())).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.noContent().build()));

        // Act
        Mono<Void> result = logoutHandler.logout(webFilterExchange, null);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(webClient).post();
    }

    @Test
    void logout_googleToken_delegatesToGoogleRevocation() {
        // Arrange
        String googleToken = "ya29.googletoken";
        when(httpHeaders.getFirst("Authorization")).thenReturn("Bearer " + googleToken);

        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.ok().build()));

        // Act
        Mono<Void> result = logoutHandler.logout(webFilterExchange, null);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(webClient).post();
    }

    @Test
    void logout_githubToken_delegatesToGithubRevocation() {
        // Arrange
        String githubToken = "gho_githubtoken";
        when(httpHeaders.getFirst("Authorization")).thenReturn("Bearer " + githubToken);
        when(githubProperties.clientId()).thenReturn("ghclientid");

        // Mock WebClient chain
        WebClient.RequestBodyUriSpec requestSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.method(HttpMethod.DELETE)).thenReturn(requestSpec);
        when(requestSpec.uri(anyString(), anyString())).thenReturn(requestSpec);
        when(requestSpec.header(anyString(), any(String[].class))).thenReturn(requestSpec);
        when(requestSpec.headers(any())).thenReturn(requestSpec);
        when(requestSpec.contentType(any(MediaType.class))).thenReturn(requestSpec);
        when(requestSpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.noContent().build()));

        // Act
        Mono<Void> result = logoutHandler.logout(webFilterExchange, null);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(webClient).method(HttpMethod.DELETE);
    }

    @Test
    void logout_facebookToken_delegatesToFacebookRevocation() {
        // Arrange
        String facebookToken = "EAAtoken";
        when(httpHeaders.getFirst("Authorization")).thenReturn("Bearer " + facebookToken);

        WebClient.RequestHeadersUriSpec uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.delete()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(headersSpec);
        when(headersSpec.header(anyString(), anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.ok().build()));

        // Act
        Mono<Void> result = logoutHandler.logout(webFilterExchange, null);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(webClient).delete();
    }
}
