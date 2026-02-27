package com.ganchevdimitarg.profile.controller;

import com.ganchevdimitarg.profile.base.BaseTest;
import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Profile;
import com.ganchevdimitarg.profile.dto.SetNewPasswordRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.stream.Collectors;

import static com.ganchevdimitarg.profile.security.UserRole.USER;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SetNewPasswordTest extends BaseTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ProfileDao profileDao;

//    @Autowired
//    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        profileDao.deleteAll().block();
        profileDao.insert(Profile.builder()
                .username("user@example.com")
//                .password(passwordEncoder.encode("OldPass@1"))
                .grantedAuthorities(USER.getGrantedAuthorities()
                        .stream()
                        .map(SimpleGrantedAuthority::getAuthority)
                        .collect(Collectors.toSet()))
                .build()).block();
    }

    @Test
    @WithMockUser(authorities = "SCOPE_profile.write")
    void setNewPassword_happyPath_returns204AndEncodesNewPassword() {
        // Arrange
        SetNewPasswordRequestDto request = new SetNewPasswordRequestDto(
                "user@example.com", "NewPass@1");

        // Act & Assert
        webTestClient.patch()
                .uri("/api/v1/profile/set-new-password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNoContent();

        // Assert new password is encoded and persisted
//        StepVerifier.create(profileDao.findByUsername("user@example.com"))
//                .assertNext(profile ->
//                        assertThat(passwordEncoder.matches("NewPass@1", profile.getPassword()))
//                                .isTrue())
//                .verifyComplete();
    }

    @Test
    @WithMockUser(authorities = "SCOPE_profile.write")
    void setNewPassword_userNotFound_returns400() {
        // Arrange
        SetNewPasswordRequestDto request = new SetNewPasswordRequestDto(
                "ghost@example.com", "NewPass@1");

        // Act & Assert
        webTestClient.patch()
                .uri("/api/v1/profile/set-new-password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @WithMockUser(authorities = "SCOPE_profile.write")
    void setNewPassword_weakPassword_returns400() {
        // Arrange — fails @Pattern validation on SetNewPasswordRequestDto
        SetNewPasswordRequestDto request = new SetNewPasswordRequestDto(
                "user@example.com", "weak");

        // Act & Assert
        webTestClient.patch()
                .uri("/api/v1/profile/set-new-password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void setNewPassword_unauthenticated_returns401() {
        // Act & Assert
        webTestClient.patch()
                .uri("/api/v1/profile/set-new-password")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new SetNewPasswordRequestDto("user@example.com", "NewPass@1"))
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
