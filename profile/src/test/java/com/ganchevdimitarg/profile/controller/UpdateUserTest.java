package com.ganchevdimitarg.profile.controller;

import com.ganchevdimitarg.profile.base.BaseTest;
import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Address;
import com.ganchevdimitarg.profile.domain.Profile;
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

import static com.ganchevdimitarg.profile.security.UserRole.USER;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UpdateUserTest extends BaseTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ProfileDao profileDao;

    /**
     * Sets up test by cleaning and inserting profile
     */
    @BeforeEach
    void setUp() {
        profileDao.deleteAll().block();
        // Inserts initial profile with attributes for testing
        profileDao.insert(Profile.builder()
                .username("user@example.com")
                .password("oldEncoded")
                .firstName("Old")
                .lastName("Name")
                .address(new Address("OldCity", "OldStreet", "0000"))
                .phoneNumber("+359000000000")
                .grantedAuthorities(USER.getGrantedAuthorities()
                        .stream()
                        .map(SimpleGrantedAuthority::getAuthority)
                        .collect(Collectors.toSet()))
                .build()).block();
    }

    @Test
    @WithMockUser(authorities = "SCOPE_profile.write")
    void updateUser_happyPath_returns204AndPersistsChanges() {
        // Arrange
        UserRequestDto request = new UserRequestDto(
                "updated@example.com", "NewPass@1",
                "Ivan", "Ivanov", "+359888000111",
                "Varna", "Main St", "9000",
                null, null, null, null);

        // Act & Assert
        webTestClient.put()
                .uri("/api/v1/profile/update-user?username=user@example.com")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNoContent();

        StepVerifier.create(profileDao.findByUsername("updated@example.com"))
                .assertNext(profile -> {
                    assertThat(profile.getFirstName()).isEqualTo("Ivan");
                    assertThat(profile.getAddress().city()).isEqualTo("Varna");
                })
                .verifyComplete();
    }

    @Test
    @WithMockUser(authorities = "SCOPE_profile.write")
    void updateUser_profileNotFound_returns404() {
        // Arrange
        UserRequestDto request = new UserRequestDto(
                "ghost@example.com", "NewPass@1",
                "Ivan", "Ivanov", "+359888000111",
                "Varna", "Main St", "9000",
                null, null, null, null);

        // Act & Assert
        webTestClient.put()
                .uri("/api/v1/profile/update-user?username=ghost@example.com")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void updateUser_unauthenticated_returns401() {
        // Act & Assert
        webTestClient.put()
                .uri("/api/v1/profile/update-user?username=user@example.com")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UserRequestDto(
                        "user@example.com", "NewPass@1",
                        "Ivan", "Ivanov", "+359888000111",
                        "Varna", "Main St", "9000",
                        null, null, null, null))
                .exchange()
                .expectStatus().isUnauthorized();
    }
}