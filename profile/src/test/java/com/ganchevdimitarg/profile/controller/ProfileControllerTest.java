package com.ganchevdimitarg.profile.controller;

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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileController Unit Tests")
class ProfileControllerTest {

    @Mock private ProfileService profileService;
    @Mock private MailService mailService;
    @Mock private Authentication authentication;
    @InjectMocks private ProfileController profileController;

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

    @Nested
    @DisplayName("createAdmin")
    class CreateAdmin {

        @Test
        @DisplayName("Happy path: returns 201 and persisted UserDto")
        void createAdmin_HappyPath_Returns201() {
            when(profileService.createAdmin(any())).thenReturn(Mono.just(userDto));
            when(mailService.sendUserWelcomeMail(anyString())).thenReturn(Mono.empty());

            StepVerifier.create(profileController.createAdmin(userRequestDto))
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                        assertThat(response.getBody()).isEqualTo(userDto);
                    })
                    .verifyComplete();

            verify(profileService).createAdmin(userRequestDto);
            verify(mailService).sendUserWelcomeMail(userDto.username());
        }

        @Test
        @DisplayName("Edge case: duplicate username — service error propagates before mail is sent")
        void createAdmin_DuplicateUsername_PropagatesErrorWithoutMail() {
            when(profileService.createAdmin(any()))
                    .thenReturn(Mono.error(new InvalidRequestDataException("Profile already exists")));

            StepVerifier.create(profileController.createAdmin(userRequestDto))
                    .expectError(InvalidRequestDataException.class)
                    .verify();

            verify(mailService, never()).sendUserWelcomeMail(anyString());
        }

        @Test
        @DisplayName("Edge case: mail service failure propagates error after profile is created")
        void createAdmin_MailFailure_PropagatesError() {
            when(profileService.createAdmin(any())).thenReturn(Mono.just(userDto));
            when(mailService.sendUserWelcomeMail(anyString()))
                    .thenReturn(Mono.error(new RuntimeException("Kafka unavailable")));

            StepVerifier.create(profileController.createAdmin(userRequestDto))
                    .expectError(RuntimeException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("createWorker")
    class CreateWorker {

        @Test
        @DisplayName("Happy path: returns 201 and persisted UserDto")
        void createWorker_HappyPath_Returns201() {
            when(profileService.createWorker(any())).thenReturn(Mono.just(userDto));
            when(mailService.sendUserWelcomeMail(anyString())).thenReturn(Mono.empty());

            StepVerifier.create(profileController.createWorker(userRequestDto))
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                        assertThat(response.getBody()).isEqualTo(userDto);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Edge case: duplicate username throws InvalidRequestDataException")
        void createWorker_DuplicateUsername_PropagatesError() {
            when(profileService.createWorker(any()))
                    .thenReturn(Mono.error(new InvalidRequestDataException("Profile already exists")));

            StepVerifier.create(profileController.createWorker(userRequestDto))
                    .expectError(InvalidRequestDataException.class)
                    .verify();

            verify(mailService, never()).sendUserWelcomeMail(anyString());
        }
    }

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("Happy path: returns 201, sends welcome mail")
        void createUser_HappyPath_Returns201() {
            when(profileService.createUser(any())).thenReturn(Mono.just(userDto));
            when(mailService.sendUserWelcomeMail(anyString())).thenReturn(Mono.empty());

            StepVerifier.create(profileController.createUser(userRequestDto))
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                        assertThat(response.getBody()).isEqualTo(userDto);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Edge case: payment service unavailable — propagates error, no mail sent")
        void createUser_PaymentUnavailable_PropagatesError() {
            when(profileService.createUser(any()))
                    .thenReturn(Mono.error(new InvalidRequestDataException("Payment service unavailable")));

            StepVerifier.create(profileController.createUser(userRequestDto))
                    .expectError(InvalidRequestDataException.class)
                    .verify();

            verify(mailService, never()).sendUserWelcomeMail(anyString());
        }
    }

    @Nested
    @DisplayName("getUserByUsername")
    class GetUserByUsername {

        @Test
        @DisplayName("Happy path: returns 200 with UserDto")
        void getUserByUsername_HappyPath_Returns200() {
            when(profileService.getUserByUsername("john.doe@example.com")).thenReturn(Mono.just(userDto));

            StepVerifier.create(profileController.getUserByUsername("john.doe@example.com"))
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(response.getBody()).isEqualTo(userDto);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Happy path: leading/trailing whitespace is trimmed before service call")
        void getUserByUsername_WhitespaceTrimmed() {
            when(profileService.getUserByUsername("john.doe@example.com")).thenReturn(Mono.just(userDto));

            StepVerifier.create(profileController.getUserByUsername("  john.doe@example.com  "))
                    .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK))
                    .verifyComplete();

            verify(profileService).getUserByUsername("john.doe@example.com");
        }

        @Test
        @DisplayName("Edge case: unknown username throws UsernameNotFoundException")
        void getUserByUsername_NotFound_PropagatesError() {
            when(profileService.getUserByUsername(anyString()))
                    .thenReturn(Mono.error(new UsernameNotFoundException("User not found")));

            StepVerifier.create(profileController.getUserByUsername("ghost@example.com"))
                    .expectError(UsernameNotFoundException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("updateUser")
    class UpdateUser {

        @Test
        @DisplayName("Happy path: returns 204 No Content")
        void updateUser_HappyPath_Returns204() {
            when(profileService.updateUser(anyString(), any())).thenReturn(Mono.empty());

            StepVerifier.create(profileController.updateUser(userRequestDto, "john.doe@example.com"))
                    .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Happy path: username whitespace is trimmed before service call")
        void updateUser_WhitespaceTrimmed() {
            when(profileService.updateUser(eq("john.doe@example.com"), any())).thenReturn(Mono.empty());

            StepVerifier.create(profileController.updateUser(userRequestDto, "  john.doe@example.com  "))
                    .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT))
                    .verifyComplete();

            verify(profileService).updateUser("john.doe@example.com", userRequestDto);
        }

        @Test
        @DisplayName("Edge case: unknown username throws UsernameNotFoundException")
        void updateUser_NotFound_PropagatesError() {
            when(profileService.updateUser(anyString(), any()))
                    .thenReturn(Mono.error(new UsernameNotFoundException("User not found")));

            StepVerifier.create(profileController.updateUser(userRequestDto, "ghost@example.com"))
                    .expectError(UsernameNotFoundException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("deleteUser")
    class DeleteUser {

        @Test
        @DisplayName("Happy path: returns 204 No Content")
        void deleteUser_HappyPath_Returns204() {
            when(authentication.getName()).thenReturn("john.doe@example.com");
            when(profileService.deleteUser("john.doe@example.com")).thenReturn(Mono.empty());

            StepVerifier.create(profileController.deleteUser(authentication))
                    .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Happy path: authentication name whitespace is trimmed")
        void deleteUser_WhitespaceTrimmed() {
            when(authentication.getName()).thenReturn("  john.doe@example.com  ");
            when(profileService.deleteUser("john.doe@example.com")).thenReturn(Mono.empty());

            StepVerifier.create(profileController.deleteUser(authentication))
                    .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT))
                    .verifyComplete();

            verify(profileService).deleteUser("john.doe@example.com");
        }

        @Test
        @DisplayName("Edge case: payment service unavailable throws InvalidRequestDataException")
        void deleteUser_PaymentUnavailable_PropagatesError() {
            when(authentication.getName()).thenReturn("john.doe@example.com");
            when(profileService.deleteUser(anyString()))
                    .thenReturn(Mono.error(new InvalidRequestDataException("Payment service unavailable")));

            StepVerifier.create(profileController.deleteUser(authentication))
                    .expectError(InvalidRequestDataException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("passwordReset")
    class PasswordReset {

        @Test
        @DisplayName("Happy path: returns 204 No Content")
        void passwordReset_HappyPath_Returns204() {
            when(profileService.passwordReset("john.doe@example.com")).thenReturn(Mono.empty());

            StepVerifier.create(profileController.passwordReset("john.doe@example.com"))
                    .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT))
                    .verifyComplete();
        }

        @Test
        @DisplayName("Happy path: username whitespace is trimmed")
        void passwordReset_WhitespaceTrimmed() {
            when(profileService.passwordReset("john.doe@example.com")).thenReturn(Mono.empty());

            StepVerifier.create(profileController.passwordReset("  john.doe@example.com  "))
                    .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT))
                    .verifyComplete();

            verify(profileService).passwordReset("john.doe@example.com");
        }

        @Test
        @DisplayName("Edge case: unknown username throws InvalidRequestDataException")
        void passwordReset_UnknownUser_PropagatesError() {
            when(profileService.passwordReset(anyString()))
                    .thenReturn(Mono.error(new InvalidRequestDataException("User not found")));

            StepVerifier.create(profileController.passwordReset("ghost@example.com"))
                    .expectError(InvalidRequestDataException.class)
                    .verify();
        }
    }

    @Nested
    @DisplayName("isValidPasswordReset")
    class IsValidPasswordReset {

        @Test
        @DisplayName("Happy path: valid token returns 200 true")
        void isValidPasswordReset_ValidToken_ReturnsTrue() {
            when(profileService.isPasswordResetTokenValid("valid.jwt.token")).thenReturn(Mono.just(true));

            StepVerifier.create(profileController.isValidPasswordReset("valid.jwt.token"))
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(response.getBody()).isTrue();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Edge case: expired/invalid token returns 200 with false body")
        void isValidPasswordReset_InvalidToken_ReturnsFalse() {
            when(profileService.isPasswordResetTokenValid("expired.token")).thenReturn(Mono.just(false));

            StepVerifier.create(profileController.isValidPasswordReset("expired.token"))
                    .assertNext(response -> {
                        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                        assertThat(response.getBody()).isFalse();
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("Happy path: token whitespace is trimmed")
        void isValidPasswordReset_WhitespaceTrimmed() {
            when(profileService.isPasswordResetTokenValid("valid.jwt.token")).thenReturn(Mono.just(true));

            StepVerifier.create(profileController.isValidPasswordReset("  valid.jwt.token  "))
                    .assertNext(response -> assertThat(response.getBody()).isTrue())
                    .verifyComplete();

            verify(profileService).isPasswordResetTokenValid("valid.jwt.token");
        }
    }

    @Nested
    @DisplayName("setNewPassword")
    class SetNewPassword {

        @Test
        @DisplayName("Happy path: returns 204 No Content")
        void setNewPassword_HappyPath_Returns204() {
            SetNewPasswordRequestDto request =
                    new SetNewPasswordRequestDto("john.doe@example.com", "NewSecurePass123!");
            when(profileService.setNewPassword(anyString(), anyString())).thenReturn(Mono.empty());

            StepVerifier.create(profileController.setNewPassword(request))
                    .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT))
                    .verifyComplete();

            verify(profileService).setNewPassword("john.doe@example.com", "NewSecurePass123!");
        }

        @Test
        @DisplayName("Happy path: username and password whitespace are trimmed")
        void setNewPassword_WhitespaceTrimmed() {
            SetNewPasswordRequestDto request =
                    new SetNewPasswordRequestDto("  john.doe@example.com  ", "  NewPass123!  ");
            when(profileService.setNewPassword("john.doe@example.com", "NewPass123!")).thenReturn(Mono.empty());

            StepVerifier.create(profileController.setNewPassword(request))
                    .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT))
                    .verifyComplete();

            verify(profileService).setNewPassword("john.doe@example.com", "NewPass123!");
        }

        @Test
        @DisplayName("Edge case: unknown username throws InvalidRequestDataException")
        void setNewPassword_UnknownUser_PropagatesError() {
            SetNewPasswordRequestDto request =
                    new SetNewPasswordRequestDto("ghost@example.com", "NewPass123!");
            when(profileService.setNewPassword(anyString(), anyString()))
                    .thenReturn(Mono.error(new InvalidRequestDataException("User not found")));

            StepVerifier.create(profileController.setNewPassword(request))
                    .expectError(InvalidRequestDataException.class)
                    .verify();
        }
    }
}
