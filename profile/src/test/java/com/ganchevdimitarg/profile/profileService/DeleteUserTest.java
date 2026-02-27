package com.ganchevdimitarg.profile.profileService;

import com.ganchevdimitarg.profile.base.BaseTest;
import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Profile;
import com.ganchevdimitarg.profile.exception.InvalidRequestDataException;
import com.ganchevdimitarg.profile.service.ProfileServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@ExtendWith(MockitoExtension.class)
class DeleteUserTest extends BaseTest {

    @Mock
    ProfileDao profileDao;
    @Mock
    WebClient webClient;
    @Mock
    ReactiveCircuitBreaker circuitBreaker;
    @InjectMocks
    ProfileServiceImpl profileService;

    @Test
    void deleteUser_happyPath_deletesProfileAndPaymentCustomer() {
        // Arrange
        Profile profile = Profile.builder().id("1").username("user@example.com").build();

        when(profileDao.findByUsername("user@example.com")).thenReturn(Mono.just(profile));
        when(circuitBreaker.run(ArgumentMatchers.<Mono<Object>>any(), ArgumentMatchers.any())).thenReturn(Mono.just("cus_123"));
        when(profileDao.delete(profile)).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = profileService.deleteUser("user@example.com");

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        verify(profileDao).delete(profile);
    }

    @Test
    void deleteUser_profileNotFound_throwsUsernameNotFoundException() {
        // Arrange
        when(profileDao.findByUsername("ghost@example.com")).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = profileService.deleteUser("ghost@example.com");

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof UsernameNotFoundException)
                .verify();
    }

    @Test
    void deleteUser_paymentServiceDown_throwsInvalidRequestDataException() {
        // Arrange
        Profile profile = Profile.builder().id("1").username("user@example.com").build();

        when(profileDao.findByUsername("user@example.com")).thenReturn(Mono.just(profile));
        when(circuitBreaker.run(ArgumentMatchers.<Mono<Object>>any(), ArgumentMatchers.any())).thenReturn(Mono.just(""));

        // Act
        Mono<Void> result = profileService.deleteUser("user@example.com");

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof InvalidRequestDataException &&
                        ex.getMessage().contains("Payment service unavailable"))
                .verify();
    }
}