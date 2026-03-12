package com.ganchevdimitarg.profile.controller;

import com.ganchevdimitarg.profile.config.BaseTest;
import com.ganchevdimitarg.profile.config.TestSecurityConfig;
import com.ganchevdimitarg.profile.dto.SetNewPasswordRequestDto;
import com.ganchevdimitarg.profile.dto.UserDto;
import com.ganchevdimitarg.profile.dto.UserRequestDto;
import com.ganchevdimitarg.profile.exception.InvalidRequestDataException;
import com.ganchevdimitarg.profile.service.MailService;
import com.ganchevdimitarg.profile.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestSecurityConfig.class)
@DisplayName("ProfileController Integration Tests")
class ProfileControllerIntegrationTest extends  BaseTest {

    private static final String BASE_URL = "/api/v1/profile";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ProfileService profileService;

    @MockitoBean
    private MailService mailService;

    private UserRequestDto userRequestDto;
    private UserDto userDto;

    @BeforeEach
    void setUp() {
        userRequestDto = new UserRequestDto(
                "john.doe@example.com",
                "SecurePass123!",
                "John",
                "Doe",
                "Varna",
                "123 Main St",
                "1111",
                "0999999999",
                "123",
                "01",
                "2033",
                "123"
        );
        userDto = UserDto.builder()
                .id("1")
                .username("john.doe@example.com")
                .password("SecurePass123!")
                .grantedAuthorities(Set.of("ROLE_USER"))
                .city("Varna")
                .firstName("John")
                .lastName("Doe")
                .street("123 Main St")
                .build();
    }

    // =========================================================================
    // POST /register-admin
    // =========================================================================

    @Nested
    @DisplayName("POST /register-admin")
    class RegisterAdmin {

        @Test
        @DisplayName("Happy path: returns 201 Created with UserDto body and sends welcome mail")
        @WithMockUser(roles = "ADMIN")
        void happyPath_Returns201AndSendsMail() {
            when(profileService.createAdmin(any())).thenReturn(Mono.just(userDto));
            when(mailService.sendUserWelcomeMail(anyString())).thenReturn(Mono.empty());

            webTestClient.post()
                    .uri(BASE_URL + "/register-admin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(userRequestDto)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(UserDto.class).isEqualTo(userDto);

            verify(mailService).sendUserWelcomeMail(userDto.username());
        }

        @Test
        @DisplayName("Edge case: duplicate username returns 400 and mail is never sent")
        @WithMockUser(roles = "ADMIN")
        void duplicateUsername_Returns400_MailNeverSent() {
            when(profileService.createAdmin(any()))
                    .thenReturn(Mono.error(new InvalidRequestDataException("Profile already exists")));

            webTestClient.post()
                    .uri(BASE_URL + "/register-admin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(userRequestDto)
                    .exchange()
                    .expectStatus().isBadRequest();

            verify(mailService, never()).sendUserWelcomeMail(anyString());
        }

        @Test
        @DisplayName("Edge case: invalid request body returns 400")
        @WithMockUser(roles = "ADMIN")
        void invalidBody_Returns400() {
            webTestClient.post()
                    .uri(BASE_URL + "/register-admin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{}")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Edge case: unauthenticated request returns 401")
        void unauthenticated_Returns401() {
            webTestClient.post()
                    .uri(BASE_URL + "/register-admin")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(userRequestDto)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    // =========================================================================
    // POST /register-worker
    // =========================================================================

    @Nested
    @DisplayName("POST /register-worker")
    class RegisterWorker {

        @Test
        @DisplayName("Happy path: returns 201 Created with UserDto body")
        @WithMockUser(roles = "ADMIN")
        void happyPath_Returns201() {
            when(profileService.createWorker(any())).thenReturn(Mono.just(userDto));
            when(mailService.sendUserWelcomeMail(anyString())).thenReturn(Mono.empty());

            webTestClient.post()
                    .uri(BASE_URL + "/register-worker")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(userRequestDto)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(UserDto.class).isEqualTo(userDto);

            verify(mailService).sendUserWelcomeMail(userDto.username());
        }

        @Test
        @DisplayName("Edge case: duplicate username returns 400")
        @WithMockUser(roles = "ADMIN")
        void duplicateUsername_Returns400() {
            when(profileService.createWorker(any()))
                    .thenReturn(Mono.error(new InvalidRequestDataException("Profile already exists")));

            webTestClient.post()
                    .uri(BASE_URL + "/register-worker")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(userRequestDto)
                    .exchange()
                    .expectStatus().isBadRequest();

            verify(mailService, never()).sendUserWelcomeMail(anyString());
        }

        @Test
        @DisplayName("Edge case: invalid request body returns 400")
        @WithMockUser(roles = "ADMIN")
        void invalidBody_Returns400() {
            webTestClient.post()
                    .uri(BASE_URL + "/register-worker")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{}")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Edge case: unauthenticated request returns 401")
        void unauthenticated_Returns401() {
            webTestClient.post()
                    .uri(BASE_URL + "/register-worker")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(userRequestDto)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    // =========================================================================
    // POST /register-user
    // =========================================================================

    @Nested
    @DisplayName("POST /register-user")
    class RegisterUser {

        @Test
        @DisplayName("Happy path: returns 201 Created with UserDto body")
        @WithMockUser
        void happyPath_Returns201() {
            when(profileService.createUser(any())).thenReturn(Mono.just(userDto));
            when(mailService.sendUserWelcomeMail(anyString())).thenReturn(Mono.empty());

            webTestClient.post()
                    .uri(BASE_URL + "/register-user")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(userRequestDto)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(UserDto.class).isEqualTo(userDto);

            verify(mailService).sendUserWelcomeMail(userDto.username());
        }

        @Test
        @DisplayName("Edge case: duplicate profile returns 400")
        @WithMockUser
        void duplicateProfile_Returns400() {
            when(profileService.createUser(any()))
                    .thenReturn(Mono.error(new InvalidRequestDataException("Profile already exists")));

            webTestClient.post()
                    .uri(BASE_URL + "/register-user")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(userRequestDto)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Edge case: payment service unavailable returns 400")
        @WithMockUser
        void paymentServiceUnavailable_Returns400() {
            when(profileService.createUser(any()))
                    .thenReturn(Mono.error(new InvalidRequestDataException("Payment service unavailable")));

            webTestClient.post()
                    .uri(BASE_URL + "/register-user")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(userRequestDto)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Edge case: invalid request body returns 400")
        @WithMockUser
        void invalidBody_Returns400() {
            webTestClient.post()
                    .uri(BASE_URL + "/register-user")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{}")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Edge case: unauthenticated request returns 401")
        void unauthenticated_Returns401() {
            webTestClient.post()
                    .uri(BASE_URL + "/register-user")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(userRequestDto)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    // =========================================================================
    // GET /get-by-username
    // =========================================================================

    @Nested
    @DisplayName("GET /get-by-username")
    class GetByUsername {

        @Test
        @DisplayName("Happy path: owner accesses own profile returns 200")
        @WithMockUser(username = "john.doe@example.com")
        void owner_Returns200() {
            when(profileService.getUserByUsername("john.doe@example.com"))
                    .thenReturn(Mono.just(userDto));

            webTestClient.get()
                    .uri(BASE_URL + "/get-by-username?username=john.doe@example.com")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(UserDto.class).isEqualTo(userDto);
        }

        @Test
        @DisplayName("Happy path: ADMIN accesses any profile returns 200")
        @WithMockUser(roles = "ADMIN")
        void admin_Returns200() {
            when(profileService.getUserByUsername("john.doe@example.com"))
                    .thenReturn(Mono.just(userDto));

            webTestClient.get()
                    .uri(BASE_URL + "/get-by-username?username=john.doe@example.com")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(UserDto.class).isEqualTo(userDto);
        }

        @Test
        @DisplayName("Happy path: GATEWAY accesses any profile returns 200")
        @WithMockUser(roles = "GATEWAY")
        void gateway_Returns200() {
            when(profileService.getUserByUsername("john.doe@example.com"))
                    .thenReturn(Mono.just(userDto));

            webTestClient.get()
                    .uri(BASE_URL + "/get-by-username?username=john.doe@example.com")
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        @DisplayName("Edge case: different user without ADMIN/GATEWAY role returns 403")
        @WithMockUser(username = "other@example.com", roles = "USER")
        void differentUser_Returns403() {
            webTestClient.get()
                    .uri(BASE_URL + "/get-by-username?username=john.doe@example.com")
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        @DisplayName("Edge case: unknown username returns 404")
        @WithMockUser(roles = "ADMIN")
        void unknownUsername_Returns404() {
            when(profileService.getUserByUsername("ghost@example.com"))
                    .thenReturn(Mono.error(new UsernameNotFoundException("User not found")));

            webTestClient.get()
                    .uri(BASE_URL + "/get-by-username?username=ghost@example.com")
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("Edge case: missing username param returns 400")
        @WithMockUser(roles = "ADMIN")
        void missingParam_Returns400() {
            webTestClient.get()
                    .uri(BASE_URL + "/get-by-username")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Edge case: unauthenticated request returns 401")
        void unauthenticated_Returns401() {
            webTestClient.get()
                    .uri(BASE_URL + "/get-by-username?username=john.doe@example.com")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    // =========================================================================
    // PUT /update-user
    // =========================================================================

    @Nested
    @DisplayName("PUT /update-user")
    class UpdateUser {

        @Test
        @DisplayName("Happy path: SCOPE_profile.write returns 204 No Content")
        @WithMockUser(authorities = "SCOPE_profile.write")
        void withWriteScope_Returns204() {
            when(profileService.updateUser(anyString(), any())).thenReturn(Mono.empty());

            webTestClient.put()
                    .uri(BASE_URL + "/update-user?username=john.doe@example.com")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(userRequestDto)
                    .exchange()
                    .expectStatus().isNoContent();

            verify(profileService).updateUser("john.doe@example.com", userRequestDto);
        }

        @Test
        @DisplayName("Happy path: ROLE_USER returns 204 No Content")
        @WithMockUser(roles = "USER")
        void withUserRole_Returns204() {
            when(profileService.updateUser(anyString(), any())).thenReturn(Mono.empty());

            webTestClient.put()
                    .uri(BASE_URL + "/update-user?username=john.doe@example.com")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(userRequestDto)
                    .exchange()
                    .expectStatus().isNoContent();
        }

        @Test
        @DisplayName("Edge case: unknown username returns 404")
        @WithMockUser(authorities = "SCOPE_profile.write")
        void unknownUsername_Returns404() {
            when(profileService.updateUser(anyString(), any()))
                    .thenReturn(Mono.error(new UsernameNotFoundException("User not found")));

            webTestClient.put()
                    .uri(BASE_URL + "/update-user?username=ghost@example.com")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(userRequestDto)
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("Edge case: invalid request body returns 400")
        @WithMockUser(authorities = "SCOPE_profile.write")
        void invalidBody_Returns400() {
            webTestClient.put()
                    .uri(BASE_URL + "/update-user?username=john.doe@example.com")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{}")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Edge case: missing username param returns 400")
        @WithMockUser(authorities = "SCOPE_profile.write")
        void missingParam_Returns400() {
            webTestClient.put()
                    .uri(BASE_URL + "/update-user")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(userRequestDto)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Edge case: insufficient authority returns 403")
        @WithMockUser(roles = "WORKER")
        void insufficientAuthority_Returns403() {
            webTestClient.put()
                    .uri(BASE_URL + "/update-user?username=john.doe@example.com")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(userRequestDto)
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        @DisplayName("Edge case: unauthenticated request returns 401")
        void unauthenticated_Returns401() {
            webTestClient.put()
                    .uri(BASE_URL + "/update-user?username=john.doe@example.com")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(userRequestDto)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    // =========================================================================
    // DELETE /delete-user
    // =========================================================================

    @Nested
    @DisplayName("DELETE /delete-user")
    class DeleteUser {

        @Test
        @DisplayName("Happy path: SCOPE_profile.write returns 204 No Content")
        @WithMockUser(username = "john.doe@example.com", authorities = "SCOPE_profile.write")
        void withWriteScope_Returns204() {
            when(profileService.deleteUser("john.doe@example.com")).thenReturn(Mono.empty());

            webTestClient.delete()
                    .uri(BASE_URL + "/delete-user")
                    .exchange()
                    .expectStatus().isNoContent();

            verify(profileService).deleteUser("john.doe@example.com");
        }

        @Test
        @DisplayName("Edge case: payment service unavailable returns 400")
        @WithMockUser(username = "john.doe@example.com", authorities = "SCOPE_profile.write")
        void paymentServiceUnavailable_Returns400() {
            when(profileService.deleteUser(anyString()))
                    .thenReturn(Mono.error(new InvalidRequestDataException("Payment service unavailable")));

            webTestClient.delete()
                    .uri(BASE_URL + "/delete-user")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Edge case: user not found returns 404")
        @WithMockUser(username = "ghost@example.com", authorities = "SCOPE_profile.write")
        void userNotFound_Returns404() {
            when(profileService.deleteUser("ghost@example.com"))
                    .thenReturn(Mono.error(new UsernameNotFoundException("User not found")));

            webTestClient.delete()
                    .uri(BASE_URL + "/delete-user")
                    .exchange()
                    .expectStatus().isNotFound();
        }

        @Test
        @DisplayName("Edge case: insufficient authority returns 403")
        @WithMockUser(username = "john.doe@example.com", roles = "USER")
        void insufficientAuthority_Returns403() {
            webTestClient.delete()
                    .uri(BASE_URL + "/delete-user")
                    .exchange()
                    .expectStatus().isForbidden();
        }

        @Test
        @DisplayName("Edge case: unauthenticated request returns 401")
        void unauthenticated_Returns401() {
            webTestClient.delete()
                    .uri(BASE_URL + "/delete-user")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    // =========================================================================
    // POST /password-reset
    // =========================================================================

    @Nested
    @DisplayName("POST /password-reset")
    class PasswordReset {

        @Test
        @DisplayName("Happy path: returns 204 No Content")
        @WithMockUser
        void happyPath_Returns204() {
            when(profileService.passwordReset("john.doe@example.com")).thenReturn(Mono.empty());

            webTestClient.post()
                    .uri(BASE_URL + "/password-reset?username=john.doe@example.com")
                    .exchange()
                    .expectStatus().isNoContent();

            verify(profileService).passwordReset("john.doe@example.com");
        }

        @Test
        @DisplayName("Edge case: unknown username returns 400")
        @WithMockUser
        void unknownUsername_Returns400() {
            when(profileService.passwordReset(anyString()))
                    .thenReturn(Mono.error(new InvalidRequestDataException("User not found")));

            webTestClient.post()
                    .uri(BASE_URL + "/password-reset?username=ghost@example.com")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Edge case: missing username param returns 400")
        @WithMockUser
        void missingParam_Returns400() {
            webTestClient.post()
                    .uri(BASE_URL + "/password-reset")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Edge case: username gets trimmed before being passed to service")
        @WithMockUser
        void usernameIsTrimmed() {
            when(profileService.passwordReset("john.doe@example.com")).thenReturn(Mono.empty());

            webTestClient.post()
                    .uri(BASE_URL + "/password-reset?username= john.doe@example.com ")
                    .exchange()
                    .expectStatus().isNoContent();

            verify(profileService).passwordReset("john.doe@example.com");
        }

        @Test
        @DisplayName("Edge case: unauthenticated request returns 401")
        void unauthenticated_Returns401() {
            webTestClient.post()
                    .uri(BASE_URL + "/password-reset?username=john.doe@example.com")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    // =========================================================================
    // GET /password-reset-token
    // =========================================================================

    @Nested
    @DisplayName("GET /password-reset-token")
    class IsValidPasswordReset {

        @Test
        @DisplayName("Happy path: valid token returns 200 true")
        @WithMockUser
        void validToken_Returns200True() {
            when(profileService.isPasswordResetTokenValid("valid.jwt.token"))
                    .thenReturn(Mono.just(true));

            webTestClient.get()
                    .uri(BASE_URL + "/password-reset-token?token=valid.jwt.token")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(true);
        }

        @Test
        @DisplayName("Edge case: expired or invalid token returns 200 false")
        @WithMockUser
        void invalidToken_Returns200False() {
            when(profileService.isPasswordResetTokenValid("expired.token"))
                    .thenReturn(Mono.just(false));

            webTestClient.get()
                    .uri(BASE_URL + "/password-reset-token?token=expired.token")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Boolean.class).isEqualTo(false);
        }

        @Test
        @DisplayName("Edge case: missing token param returns 400")
        @WithMockUser
        void missingParam_Returns400() {
            webTestClient.get()
                    .uri(BASE_URL + "/password-reset-token")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Edge case: unauthenticated request returns 401")
        void unauthenticated_Returns401() {
            webTestClient.get()
                    .uri(BASE_URL + "/password-reset-token?token=some.token")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    // =========================================================================
    // PATCH /set-new-password
    // =========================================================================

    @Nested
    @DisplayName("PATCH /set-new-password")
    class SetNewPassword {

        @Test
        @DisplayName("Happy path: returns 204 No Content and delegates trimmed values to service")
        @WithMockUser
        void happyPath_Returns204() {
            SetNewPasswordRequestDto request =
                    new SetNewPasswordRequestDto("john.doe@example.com", "NewSecurePass123!");
            when(profileService.setNewPassword(anyString(), anyString())).thenReturn(Mono.empty());

            webTestClient.patch()
                    .uri(BASE_URL + "/set-new-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isNoContent();

            verify(profileService).setNewPassword("john.doe@example.com", "NewSecurePass123!");
        }

        @Test
        @DisplayName("Edge case: unknown username returns 400")
        @WithMockUser
        void unknownUsername_Returns400() {
            SetNewPasswordRequestDto request =
                    new SetNewPasswordRequestDto("ghost@example.com", "NewPass123!");
            when(profileService.setNewPassword(anyString(), anyString()))
                    .thenReturn(Mono.error(new InvalidRequestDataException("User not found")));

            webTestClient.patch()
                    .uri(BASE_URL + "/set-new-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Edge case: invalid request body returns 400")
        @WithMockUser
        void invalidBody_Returns400() {
            webTestClient.patch()
                    .uri(BASE_URL + "/set-new-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{}")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("Edge case: unauthenticated request returns 401")
        void unauthenticated_Returns401() {
            SetNewPasswordRequestDto request =
                    new SetNewPasswordRequestDto("john.doe@example.com", "NewSecurePass123!");

            webTestClient.patch()
                    .uri(BASE_URL + "/set-new-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }
}