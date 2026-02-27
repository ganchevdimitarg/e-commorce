package com.ganchevdimitarg.profile.jwtService;

import com.ganchevdimitarg.profile.base.BaseTest;
import com.ganchevdimitarg.profile.dao.PasswordResetDao;
import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Profile;
import com.ganchevdimitarg.profile.service.JwtServiceImpl;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.stream.Collectors;

import static com.ganchevdimitarg.profile.security.UserRole.USER;

@Testcontainers
@SpringBootTest
class IsTokenValidTest extends BaseTest {

    @Autowired
    private JwtServiceImpl jwtService;

    @Autowired
    private PasswordResetDao passwordResetDao;

    @Autowired
    private ProfileDao profileDao;

    @BeforeEach
    void setUp() {
        passwordResetDao.deleteAll().block();
        profileDao.deleteAll().block();
    }

    @Test
    void isTokenValid_happyPath_returnsTrueWhenAllChecksPass() {
        // Arrange
        Profile profile = Profile.builder()
                .username("user@example.com")
                .password("encoded")
                .grantedAuthorities(USER.getGrantedAuthorities()
                        .stream()
                        .map(SimpleGrantedAuthority::getAuthority)
                        .collect(Collectors.toSet()))
                .build();
        profileDao.insert(profile).block();

        UserDetails user = new User("user@example.com", "", USER.getGrantedAuthorities());
        String token = jwtService.generateToken(user).block();

        // Act
        Mono<Boolean> result = jwtService.isTokenValid(token);

        // Assert
        StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void isTokenValid_tokenNotInStore_returnsFalse() {
        // Arrange
        Profile profile = Profile.builder()
                .username("user@example.com")
                .password("encoded")
                .grantedAuthorities(USER.getGrantedAuthorities().stream()
                        .map(SimpleGrantedAuthority::getAuthority)
                        .collect(Collectors.toSet()))
                .build();
        profileDao.insert(profile).block();

        // Generate token but don't save it to passwordResetDao
        String token = Jwts.builder()
                .subject("user@example.com")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)))
                .signWith(jwtService.getSigningKey())
                .compact();

        // Act
        Mono<Boolean> result = jwtService.isTokenValid(token);

        // Assert
        StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void isTokenValid_userNotInProfileStore_returnsFalse() {
        // Arrange — token saved but no matching profile
        UserDetails user = new User("ghost@example.com", "", USER.getGrantedAuthorities());
        String token = jwtService.generateToken(user).block();

        // Act
        Mono<Boolean> result = jwtService.isTokenValid(token);

        // Assert
        StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void isTokenValid_expiredToken_returnsFalse() {
        // Arrange — build an already-expired token directly
        String expiredToken = Jwts.builder()
                .subject("user@example.com")
                .issuedAt(Date.from(Instant.now().minus(20, ChronoUnit.MINUTES)))
                .expiration(Date.from(Instant.now().minus(10, ChronoUnit.MINUTES)))
                .signWith(jwtService.getSigningKey())
                .compact();

        // Act
        Mono<Boolean> result = jwtService.isTokenValid(expiredToken);

        // Assert
        StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete();
    }
}