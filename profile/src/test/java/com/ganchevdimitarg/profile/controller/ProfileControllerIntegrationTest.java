package com.ganchevdimitarg.profile.controller;

import com.ganchevdimitarg.profile.config.BaseTest;
import com.ganchevdimitarg.profile.config.TestSecurityConfig;
import com.ganchevdimitarg.profile.dto.CardSetupCommand;
import com.ganchevdimitarg.profile.dto.UpdateProfileCommand;
import com.ganchevdimitarg.profile.dto.UserDto;
import com.ganchevdimitarg.profile.exception.InvalidRequestDataException;
import com.ganchevdimitarg.profile.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

@SpringBootTest
@Import(TestSecurityConfig.class)
@DisplayName("ProfileController Integration Tests")
class ProfileControllerIntegrationTest extends BaseTest {

    private static final String BASE_URL = "/api/v1/profile";

    @Autowired
    private ApplicationContext context;

    @MockitoBean
    private ProfileService profileService;

    private WebTestClient webTestClient;
    private UserDto userDto;
    private UpdateProfileCommand updateCommand;
    private CardSetupCommand cardCommand;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToApplicationContext(context)
                .apply(springSecurity())
                .configureClient()
                .build();
        userDto = UserDto.builder()
                .userId("user")
                .firstName("John").lastName("Doe")
                .phoneNumber("0999999999")
                .city("Varna").street("123 Main St").postCode("1111")
                .cardId("card_456")
                .build();
        updateCommand = new UpdateProfileCommand(
                "John", "Doe", "Varna", "123 Main St", "1111", "0999999999");
        cardCommand = new CardSetupCommand("4242424242424242", "03", "2026", "314");
    }

    @Nested
    @DisplayName("GET /me")
    class GetMe {

        @Test
        @DisplayName("Happy path: returns 200 with the authenticated user's profile")
        void happyPath_Returns200() {
            when(profileService.getByUserId("user")).thenReturn(Mono.just(userDto));

            webTestClient.mutateWith(mockUser("user").roles("USER"))
                    .get().uri(BASE_URL + "/me")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(UserDto.class).isEqualTo(userDto);

            verify(profileService).getByUserId("user");
        }

        @Test
        @DisplayName("Edge case: unknown profile returns 400")
        void unknownProfile_Returns400() {
            when(profileService.getByUserId(anyString()))
                    .thenReturn(Mono.error(new InvalidRequestDataException("Profile does not exist")));

            webTestClient.mutateWith(mockUser("user").roles("USER"))
                    .get().uri(BASE_URL + "/me")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Edge case: unauthenticated request returns 401")
        void unauthenticated_Returns401() {
            webTestClient.get().uri(BASE_URL + "/me")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("PUT /me")
    class UpdateMe {

        @Test
        @DisplayName("Happy path: ROLE_USER returns 204 No Content")
        void happyPath_Returns204() {
            when(profileService.updateProfile(anyString(), any())).thenReturn(Mono.empty());

            webTestClient.mutateWith(mockUser("user").roles("USER"))
                    .put().uri(BASE_URL + "/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(updateCommand)
                    .exchange()
                    .expectStatus().isNoContent();

            verify(profileService).updateProfile("user", updateCommand);
        }

        @Test
        @DisplayName("Happy path: SCOPE_profile.write returns 204 No Content")
        void withWriteScope_Returns204() {
            when(profileService.updateProfile(anyString(), any())).thenReturn(Mono.empty());

            webTestClient.mutateWith(mockUser("user")
                            .authorities(new SimpleGrantedAuthority("SCOPE_profile.write")))
                    .put().uri(BASE_URL + "/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(updateCommand)
                    .exchange()
                    .expectStatus().isNoContent();
        }

        @Test
        @DisplayName("Edge case: invalid request body returns 400")
        void invalidBody_Returns400() {
            webTestClient.mutateWith(mockUser("user").roles("USER"))
                    .put().uri(BASE_URL + "/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{}")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Edge case: insufficient authority returns 403")
        void insufficientAuthority_Returns403() {
            webTestClient.mutateWith(mockUser("user").roles("WORKER"))
                    .put().uri(BASE_URL + "/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(updateCommand)
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        @DisplayName("Edge case: unauthenticated request returns 401")
        void unauthenticated_Returns401() {
            webTestClient.put().uri(BASE_URL + "/me")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(updateCommand)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("POST /payment-setup")
    class PaymentSetup {

        @Test
        @DisplayName("Happy path: ROLE_USER returns 201 with the enriched UserDto")
        void happyPath_Returns201() {
            when(profileService.setupPayment(anyString(), any())).thenReturn(Mono.just(userDto));

            webTestClient.mutateWith(mockUser("user").roles("USER"))
                    .post().uri(BASE_URL + "/payment-setup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(cardCommand)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(UserDto.class).isEqualTo(userDto);

            verify(profileService).setupPayment("user", cardCommand);
        }

        @Test
        @DisplayName("Edge case: payment service unavailable returns 400")
        void paymentUnavailable_Returns400() {
            when(profileService.setupPayment(anyString(), any()))
                    .thenReturn(Mono.error(new InvalidRequestDataException("Payment service unavailable")));

            webTestClient.mutateWith(mockUser("user").roles("USER"))
                    .post().uri(BASE_URL + "/payment-setup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(cardCommand)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Edge case: invalid request body returns 400")
        void invalidBody_Returns400() {
            webTestClient.mutateWith(mockUser("user").roles("USER"))
                    .post().uri(BASE_URL + "/payment-setup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{}")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Edge case: unauthenticated request returns 401")
        void unauthenticated_Returns401() {
            webTestClient.post().uri(BASE_URL + "/payment-setup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(cardCommand)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }
}
