package com.ganchevdimitarg.profile.controller;

import com.ganchevdimitarg.profile.dto.CardSetupCommand;
import com.ganchevdimitarg.profile.dto.UpdateProfileCommand;
import com.ganchevdimitarg.profile.dto.UserDto;
import com.ganchevdimitarg.profile.exception.InvalidRequestDataException;
import com.ganchevdimitarg.profile.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileController Unit Tests")
class ProfileControllerTest {

    private static final String USER_ID = "9f1c0000-0000-0000-0000-000000000001";

    @Mock private ProfileService profileService;
    @Mock private Authentication authentication;
    @InjectMocks private ProfileController profileController;

    private UserDto userDto;

    @BeforeEach
    void setUp() {
        userDto = UserDto.builder()
                .userId(USER_ID)
                .firstName("John").lastName("Doe")
                .phoneNumber("0999999999")
                .city("Varna").street("123 Main St").postCode("1111")
                .cardId("card_456")
                .build();
    }

    @Test
    @DisplayName("getMe: returns 200 with the authenticated user's profile")
    void should_returnProfile_when_getMe() {
        when(authentication.getName()).thenReturn(USER_ID);
        when(profileService.getByUserId(USER_ID)).thenReturn(Mono.just(userDto));

        StepVerifier.create(profileController.getMe(authentication))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody()).isEqualTo(userDto);
                })
                .verifyComplete();

        verify(profileService).getByUserId(USER_ID);
    }

    @Test
    @DisplayName("getMe: unknown profile propagates InvalidRequestDataException")
    void should_propagateError_when_getMeNotFound() {
        when(authentication.getName()).thenReturn(USER_ID);
        when(profileService.getByUserId(USER_ID))
                .thenReturn(Mono.error(new InvalidRequestDataException("Profile does not exist")));

        StepVerifier.create(profileController.getMe(authentication))
                .expectError(InvalidRequestDataException.class)
                .verify();
    }

    @Test
    @DisplayName("updateMe: returns 204 No Content")
    void should_return204_when_updateMe() {
        UpdateProfileCommand cmd = new UpdateProfileCommand(
                "John", "Doe", "Varna", "123 Main St", "1111", "0999999999");
        when(authentication.getName()).thenReturn(USER_ID);
        when(profileService.updateProfile(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(profileController.updateMe(authentication, cmd))
                .assertNext(response -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT))
                .verifyComplete();

        verify(profileService).updateProfile(USER_ID, cmd);
    }

    @Test
    @DisplayName("setupPayment: returns 201 with the enriched UserDto")
    void should_runPaymentSaga_when_paymentSetup() {
        CardSetupCommand cmd = new CardSetupCommand("4242424242424242", "03", "2026", "314");
        when(authentication.getName()).thenReturn(USER_ID);
        when(profileService.setupPayment(USER_ID, cmd)).thenReturn(Mono.just(userDto));

        StepVerifier.create(profileController.setupPayment(authentication, cmd))
                .assertNext(response -> {
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    assertThat(response.getBody()).isEqualTo(userDto);
                })
                .verifyComplete();

        verify(profileService).setupPayment(USER_ID, cmd);
    }

    @Test
    @DisplayName("setupPayment: payment failure propagates error")
    void should_propagateError_when_paymentSetupFails() {
        CardSetupCommand cmd = new CardSetupCommand("4242424242424242", "03", "2026", "314");
        when(authentication.getName()).thenReturn(USER_ID);
        when(profileService.setupPayment(USER_ID, cmd))
                .thenReturn(Mono.error(new InvalidRequestDataException("Payment service unavailable")));

        StepVerifier.create(profileController.setupPayment(authentication, cmd))
                .expectError(InvalidRequestDataException.class)
                .verify();
    }
}
