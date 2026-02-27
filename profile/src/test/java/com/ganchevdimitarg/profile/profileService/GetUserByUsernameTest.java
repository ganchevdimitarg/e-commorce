package com.ganchevdimitarg.profile.profileService;

import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Address;
import com.ganchevdimitarg.profile.domain.Profile;
import com.ganchevdimitarg.profile.dto.UserDto;
import com.ganchevdimitarg.profile.service.ProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Set;
import java.util.stream.Collectors;

import static com.ganchevdimitarg.profile.security.UserRole.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetUserByUsernameTest {

    @Mock
    ProfileDao profileDao;
    @Mock
    WebClient webClient;
    @Mock
    WebClient.RequestHeadersUriSpec<?> uriSpec;
    @Mock
    WebClient.RequestHeadersSpec headersSpec;
    @Mock
    WebClient.ResponseSpec responseSpec;
    @Mock
    ReactiveCircuitBreaker circuitBreaker;

    @InjectMocks
    ProfileServiceImpl profileService;

    private final Profile profile = Profile.builder()
            .id("1")
            .username("user@example.com")
            .firstName("Ivan")
            .lastName("Ivanov")
            .phoneNumber("+359888000111")
            .address(new Address("Varna", "Main St", "9000"))
            .grantedAuthorities(USER.getGrantedAuthorities()
                    .stream()
                    .map(SimpleGrantedAuthority::getAuthority)
                    .collect(Collectors.toSet())
            )
            .build();

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        when(webClient.get()).thenReturn((WebClient.RequestHeadersUriSpec) uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    void getUserByUsername_happyPath_returnsUserDtoWithCardId() {
        // Arrange
        when(profileDao.findByUsername("user@example.com")).thenReturn(Mono.just(profile));
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(Set.of("card_123")));
        when(circuitBreaker.run(ArgumentMatchers.<Mono<Object>>any(), ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));
        // Act
        Mono<UserDto> result = profileService.getUserByUsername("user@example.com");

        // Assert
        StepVerifier.create(result)
                .assertNext(dto -> {
                    assertThat(dto.username()).isEqualTo("user@example.com");
                    assertThat(dto.cardId()).isEqualTo("card_123");
                })
                .verifyComplete();
    }

    @Test
    void getUserByUsername_paymentServiceDown_returnsUserDtoWithEmptyCardId() {
        // Arrange
        when(profileDao.findByUsername("user@example.com")).thenReturn(Mono.just(profile));
        when(responseSpec.bodyToMono(any(ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(new RuntimeException("Payment service down")));
        when(circuitBreaker.run(ArgumentMatchers.<Mono<Object>>any(), ArgumentMatchers.any()))
                .thenReturn(Mono.just(Set.of("")));

        // Act
        Mono<UserDto> result = profileService.getUserByUsername("user@example.com");

        // Assert
        StepVerifier.create(result)
                .assertNext(dto -> assertThat(dto.cardId()).isEmpty())
                .verifyComplete();
    }

    @Test
    void getUserByUsername_profileNotFound_throwsUsernameNotFoundException() {
        // Arrange
        when(profileDao.findByUsername("ghost@example.com")).thenReturn(Mono.empty());

        // Act
        Mono<UserDto> result = profileService.getUserByUsername("ghost@example.com");

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof UsernameNotFoundException &&
                        ex.getMessage().equals("Profile does not exist"))
                .verify();
    }
}