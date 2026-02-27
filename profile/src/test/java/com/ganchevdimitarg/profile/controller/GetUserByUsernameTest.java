package com.ganchevdimitarg.profile.controller;

import com.ganchevdimitarg.profile.base.BaseTest;
import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Address;
import com.ganchevdimitarg.profile.domain.Profile;
import com.ganchevdimitarg.profile.dto.UserDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.stream.Collectors;

import static com.ganchevdimitarg.profile.security.UserRole.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GetUserByUsernameTest extends BaseTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ProfileDao profileDao;

    @MockitoBean
    private WebClient webClient;

    @BeforeEach
    void setUp() {
        profileDao.deleteAll().block();

        // Stub payment card lookup
        WebClient.RequestHeadersUriSpec<?> uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.get()).thenReturn((WebClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(Set.of("card_123")));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void getUserByUsername_owner_returns200() {
        // Arrange
        profileDao.insert(Profile.builder()
                .username("user@example.com")
                .firstName("Ivan")
                .lastName("Ivanov")
                .address(new Address("Varna", "Main St", "9000"))
                .phoneNumber("+359888000111")
                .grantedAuthorities(USER.getGrantedAuthorities()
                        .stream()
                        .map(SimpleGrantedAuthority::getAuthority)
                        .collect(Collectors.toSet()))
                .build()).block();

        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/profile/get-by-username?username=user@example.com")
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserDto.class)
                .value(dto -> {
                    assertThat(dto.username()).isEqualTo("user@example.com");
                    assertThat(dto.cardId()).isEqualTo("card_123");
                });
    }

    @Test
    @WithMockUser(username = "other@example.com")
    void getUserByUsername_differentUser_returns403() {
        // Arrange
        profileDao.insert(Profile.builder()
                .username("user@example.com")
                .grantedAuthorities(USER.getGrantedAuthorities().stream()
                        .map(SimpleGrantedAuthority::getAuthority)
                        .collect(Collectors.toSet()))
                .build()).block();

        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/profile/get-by-username?username=user@example.com")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUserByUsername_admin_canAccessAnyProfile() {
        // Arrange
        profileDao.insert(Profile.builder()
                .username("user@example.com")
                .firstName("Ivan")
                .lastName("Ivanov")
                .address(new Address("Varna", "Main St", "9000"))
                .phoneNumber("+359888000111")
                .grantedAuthorities(USER.getGrantedAuthorities().stream()
                        .map(SimpleGrantedAuthority::getAuthority)
                        .collect(Collectors.toSet()))
                .build()).block();

        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/profile/get-by-username?username=user@example.com")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUserByUsername_profileNotFound_returns404() {
        // Act & Assert
        webTestClient.get()
                .uri("/api/v1/profile/get-by-username?username=ghost@example.com")
                .exchange()
                .expectStatus().isNotFound();
    }
}
