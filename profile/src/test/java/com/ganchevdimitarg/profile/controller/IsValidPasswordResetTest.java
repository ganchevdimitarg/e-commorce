package com.ganchevdimitarg.profile.controller;

import com.ganchevdimitarg.profile.base.BaseTest;
import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Profile;
import com.ganchevdimitarg.profile.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.stream.Collectors;

import static com.ganchevdimitarg.profile.security.UserRole.USER;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IsValidPasswordResetTest extends BaseTest {


    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ProfileDao profileDao;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        profileDao.deleteAll().block();
        profileDao.insert(Profile.builder()
                .username("user@example.com")
                .password("encoded")
                .grantedAuthorities(USER.getGrantedAuthorities()
                        .stream()
                        .map(SimpleGrantedAuthority::getAuthority)
                        .collect(Collectors.toSet()))
                .build()).block();
    }

    @Test
    void isValidPasswordReset_validToken_returns200True() {
        // Arrange
        String token = jwtService.generateToken(
                        new User("user@example.com", "", USER.getGrantedAuthorities()))
                .block();

        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/profile/password-reset-token?token=" + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class)
                .isEqualTo(true);
    }

    @Test
    void isValidPasswordReset_invalidToken_returns200False() {
        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/profile/password-reset-token?token=not.a.valid.token")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Boolean.class)
                .isEqualTo(false);
    }
}
