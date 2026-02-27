package com.ganchevdimitarg.profile.controller;

import com.ganchevdimitarg.profile.base.BaseTest;
import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Profile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.stream.Collectors;

import static com.ganchevdimitarg.profile.security.UserRole.USER;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeleteUserTest extends BaseTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ProfileDao profileDao;

    @MockitoBean
    private WebClient webClient;

    @BeforeEach
    void setUp() {
        profileDao.deleteAll().block();

        // Stub payment customer deletion
        WebClient.RequestHeadersUriSpec<?> uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.delete()).thenReturn((WebClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("cus_123"));
    }

    @Test
    @WithMockUser(username = "user@example.com", authorities = "SCOPE_profile.write")
    void deleteUser_happyPath_returns204AndRemovesProfile() {
        // Arrange
        profileDao.insert(Profile.builder()
                .username("user@example.com")
                .password("encoded")
                .grantedAuthorities(USER.getGrantedAuthorities()
                        .stream()
                        .map(SimpleGrantedAuthority::getAuthority)
                        .collect(Collectors.toSet()))
                .build()).block();

        // Act & Assert
        webTestClient.delete()
                .uri("/api/v1/profile/delete-user")
                .exchange()
                .expectStatus().isNoContent();

        StepVerifier.create(profileDao.findByUsername("user@example.com"))
                .verifyComplete();
    }

    @Test
    @WithMockUser(username = "ghost@example.com", authorities = "SCOPE_profile.write")
    void deleteUser_profileNotFound_returns404() {
        // Act & Assert
        webTestClient.delete()
                .uri("/api/v1/profile/delete-user")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void deleteUser_unauthenticated_returns401() {
        // Act & Assert
        webTestClient.delete()
                .uri("/api/v1/profile/delete-user")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}