package com.ganchevdimitarg.profile.profileService;

import com.ganchevdimitarg.profile.base.BaseTest;
import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Profile;
import com.ganchevdimitarg.profile.exception.InvalidRequestDataException;
import com.ganchevdimitarg.profile.service.ProfileServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;

@Testcontainers
@ExtendWith(MockitoExtension.class)
class SetNewPasswordTest extends BaseTest {

    @Mock
    ProfileDao profileDao;
    @Mock
    PasswordEncoder passwordEncoder;
    @InjectMocks
    ProfileServiceImpl profileService;

    @Test
    void setNewPassword_happyPath_encodesAndSavesPassword() {
        // Arrange
        Profile profile = Profile.builder().id("1").username("user@example.com").build();

        when(profileDao.findByUsername("user@example.com")).thenReturn(Mono.just(profile));
        when(passwordEncoder.encode("NewPass@1")).thenReturn("encodedNewPass");
        when(profileDao.save(any())).thenReturn(Mono.just(profile));

        // Act
        Mono<Void> result = profileService.setNewPassword("user@example.com", "NewPass@1");

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(passwordEncoder).encode("NewPass@1");
        verify(profileDao).save(argThat(p -> p.getPassword().equals("encodedNewPass")));
    }

    @Test
    void setNewPassword_userNotFound_throwsInvalidRequestDataException() {
        // Arrange
        when(profileDao.findByUsername("ghost@example.com")).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = profileService.setNewPassword("ghost@example.com", "NewPass@1");

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof InvalidRequestDataException &&
                        ex.getMessage().equals("User does not exist"))
                .verify();

        verifyNoInteractions(passwordEncoder);
    }
}