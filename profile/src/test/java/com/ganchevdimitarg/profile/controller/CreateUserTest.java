package com.ganchevdimitarg.profile.controller;

import com.ganchevdimitarg.profile.base.BaseTest;
import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.dto.PaymentDto;
import com.ganchevdimitarg.profile.dto.UserDto;
import com.ganchevdimitarg.profile.dto.UserRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.stream.Collectors;

import static com.ganchevdimitarg.profile.security.UserRole.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CreateUserTest extends BaseTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ProfileDao profileDao;

    @MockitoBean
    private WebClient webClient;

    /**
     * Sets up mocked dependencies and test data
     */
    @BeforeEach
    void setUp() {
        profileDao.deleteAll().block();

        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.accept(any())).thenReturn(bodySpec);

        when(bodySpec.bodyValue(any())).thenReturn(headersSpec);

        when(headersSpec.retrieve()).thenReturn(responseSpec);
        // Returns mocked payment data for testing
        when(responseSpec.bodyToMono(PaymentDto.class))
                .thenReturn(Mono.just(PaymentDto.builder()
                        .customerId("cus_123")
                        .cardId("card_456")
                        .build()));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createUser_happyPath_returns201WithCardId() {
        // Arrange
        UserRequestDto request = new UserRequestDto(
                "user@example.com", "Pass@1234",
                "Ivan", "Ivanov", "+359888000111",
                "Varna", "Main St", "9000",
                "4242424242424242", "03", "2026", "314");

        // Act & Assert HTTP
        webTestClient.post()
                .uri("/api/v1/profile/register-user")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserDto.class)
                .value(dto -> {
                    assertThat(dto.username()).isEqualTo("user@example.com");
                    assertThat(dto.cardId()).isEqualTo("card_456");
                    assertThat(dto.grantedAuthorities())
                            .containsAll(USER.getGrantedAuthorities()
                                    .stream()
                                    .map(SimpleGrantedAuthority::getAuthority)
                                    .collect(Collectors.toSet()));
                });

        // Assert profile persisted in MongoDB
        StepVerifier.create(profileDao.findByUsername("user@example.com"))
                .assertNext(profile ->
                        assertThat(profile.getUsername()).isEqualTo("user@example.com"))
                .verifyComplete();
    }

    @Test
    @WithMockUser(roles = "USER")
    void createUser_duplicateUsername_returns400() {
        // Arrange
        UserRequestDto request = new UserRequestDto(
                "user@example.com", "Pass@1234",
                "Ivan", "Ivanov", "+359888000111",
                "Varna", "Main St", "9000",
                "4242424242424242", "03", "2026", "314");

        // Insert an existing profile to trigger a duplicate check
        webTestClient.post()
                .uri("/api/v1/profile/register-user")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated();

        // Act & Assert - second registration with the same username
        webTestClient.post()
                .uri("/api/v1/profile/register-user")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @WithMockUser(roles = "USER")
    void createUser_paymentServiceDown_returns400() {
        // Arrange
        WebClient.RequestBodyUriSpec failingUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec failingBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec failingHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec failingResponseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(failingUriSpec);
        when(failingUriSpec.uri(anyString())).thenReturn(failingBodySpec);
        when(failingBodySpec.contentType(any())).thenReturn(failingBodySpec);
        when(failingBodySpec.accept(any())).thenReturn(failingBodySpec);
        when(failingHeadersSpec).thenReturn(failingBodySpec);
        when(failingHeadersSpec.retrieve()).thenReturn(failingResponseSpec);
        when(failingResponseSpec.bodyToMono(PaymentDto.class))
                .thenReturn(Mono.just(PaymentDto.builder()
                        .customerId("")
                        .build()));

        UserRequestDto request = new UserRequestDto(
                "user@example.com", "Pass@1234",
                "Ivan", "Ivanov", "+359888000111",
                "Varna", "Main St", "9000",
                "4242424242424242", "03", "2026", "314");

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/profile/register-user")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @WithMockUser(roles = "USER")
    void createUser_invalidRequestBody_returns400() {
        // Arrange - invalid email format
        UserRequestDto request = new UserRequestDto(
                "not-an-email", "Pass@1234",
                "Ivan", "Ivanov", "+359888000111",
                "Varna", "Main St", "9000",
                "4242424242424242", "03", "2026", "314");

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/profile/register-user")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void createUser_unauthenticated_returns401() {
        // Arrange
        UserRequestDto request = new UserRequestDto(
                "user@example.com", "Pass@1234",
                "Ivan", "Ivanov", "+359888000111",
                "Varna", "Main St", "9000",
                "4242424242424242", "03", "2026", "314");

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/profile/register-user")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}