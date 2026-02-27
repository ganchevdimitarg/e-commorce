package com.ganchevdimitarg.profile.profileService;

import com.ganchevdimitarg.profile.service.JwtService;
import com.ganchevdimitarg.profile.service.ProfileServiceImpl;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IsPasswordResetTokenValidTest {

    @Mock
    JwtService jwtService;
    @InjectMocks
    ProfileServiceImpl profileService;

    @Test
    void isPasswordResetTokenValid_validToken_returnsTrue() {
        // Arrange
        when(jwtService.isTokenValid("valid.jwt.token")).thenReturn(Mono.just(true));

        // Act
        Mono<Boolean> result = profileService.isPasswordResetTokenValid("valid.jwt.token");

        // Assert
        StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void isPasswordResetTokenValid_expiredToken_returnsFalse() {
        // Arrange
        when(jwtService.isTokenValid("expired.jwt.token")).thenReturn(Mono.just(false));

        // Act
        Mono<Boolean> result = profileService.isPasswordResetTokenValid("expired.jwt.token");

        // Assert
        StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void isPasswordResetTokenValid_invalidToken_returnsFalse() {
        // Arrange
        when(jwtService.isTokenValid("bad-token"))
                .thenReturn(Mono.error(new JwtException("Malformed token")));

        // Act
        Mono<Boolean> result = profileService.isPasswordResetTokenValid("bad-token");

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof JwtException)
                .verify();
    }
}
