package com.ganchevdimitarg.profile.controller;

import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Profile;
import com.ganchevdimitarg.profile.dto.UserDto;
import com.ganchevdimitarg.profile.dto.UserRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.util.stream.Collectors;

import static com.ganchevdimitarg.profile.security.UserRole.WORKER;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CreateWorkerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ProfileDao profileDao;

    @BeforeEach
    void setUp() {
        profileDao.deleteAll().block();

    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createWorker_happyPath_returns201AndWorkerAuthorities() {
        // Arrange
        UserRequestDto request = new UserRequestDto(
                "worker@example.com", "Pass@1234",
                "Ivan", "Ivanov", "+359888000111",
                "Varna", "Main St", "9000",
                null, null, null, null);

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/profile/register-worker")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserDto.class)
                .value(dto -> {
                    assertThat(dto.username()).isEqualTo("worker@example.com");
                    assertThat(dto.cardId()).isEmpty();
                    assertThat(dto.grantedAuthorities())
                            .containsAll(WORKER.getGrantedAuthorities()
                                    .stream()
                                    .map(SimpleGrantedAuthority::getAuthority)
                                    .collect(Collectors.toSet()));
                });

        // Verifies worker profile has expected authorities
        StepVerifier.create(profileDao.findByUsername("worker@example.com"))
                .assertNext(profile ->
                        assertThat(profile.getGrantedAuthorities())
                                .containsAll(WORKER.getGrantedAuthorities()
                                        .stream()
                                        .map(SimpleGrantedAuthority::getAuthority)
                                        .collect(Collectors.toSet())))
                .verifyComplete();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createWorker_duplicateUsername_returns400() {
        // Arrange
        profileDao.insert(Profile.builder()
                .username("worker@example.com")
                .password("encoded")
                .grantedAuthorities(WORKER.getGrantedAuthorities()
                        .stream()
                        .map(SimpleGrantedAuthority::getAuthority)
                        .collect(Collectors.toSet()))
                .build()).block();

        UserRequestDto request = new UserRequestDto(
                "worker@example.com", "Pass@1234",
                "Ivan", "Ivanov", "+359888000111",
                "Varna", "Main St", "9000",
                null, null, null, null);

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/profile/register-worker")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }
}
