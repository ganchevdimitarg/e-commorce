package com.ganchevdimitarg.profile.profileService;

import com.ganchevdimitarg.profile.base.BaseTest;
import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Profile;
import com.ganchevdimitarg.profile.exception.InvalidRequestDataException;
import com.ganchevdimitarg.profile.service.JwtService;
import com.ganchevdimitarg.profile.service.MailService;
import com.ganchevdimitarg.profile.service.ProfileServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

@Testcontainers
@ExtendWith(MockitoExtension.class)
class PasswordResetTest extends BaseTest {

    @Mock
    ProfileDao profileDao;
    @Mock
    JwtService jwtService;
    @Mock
    MailService mailService;
    @InjectMocks
    ProfileServiceImpl profileService;

    @Test
    void passwordReset_happyPath_sendsEmailAndCompletes() {
        // Arrange
        Profile profile = Profile.builder().username("user@example.com").build();

        when(profileDao.findByUsername("user@example.com")).thenReturn(Mono.just(profile));
        when(jwtService.generateToken(any())).thenReturn(Mono.just("reset-token-xyz"));
        when(mailService.sendPasswordResetTokenMail("user@example.com", "reset-token-xyz"))
                .thenReturn(Mono.empty());

        // Act
        Mono<Void> result = profileService.passwordReset("user@example.com");

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(mailService).sendPasswordResetTokenMail("user@example.com", "reset-token-xyz");
    }

    @Test
    void passwordReset_userNotFound_throwsInvalidRequestDataException() {
        // Arrange
        when(profileDao.findByUsername("ghost@example.com")).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = profileService.passwordReset("ghost@example.com");

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof InvalidRequestDataException &&
                        ex.getMessage().equals("User does not exist"))
                .verify();

        verifyNoInteractions(jwtService, mailService);
    }
}