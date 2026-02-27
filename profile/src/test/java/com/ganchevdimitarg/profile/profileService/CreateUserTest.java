package com.ganchevdimitarg.profile.profileService;

import com.ganchevdimitarg.profile.base.BaseTest;
import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Address;
import com.ganchevdimitarg.profile.domain.Profile;
import com.ganchevdimitarg.profile.dto.PaymentDto;
import com.ganchevdimitarg.profile.dto.UserDto;
import com.ganchevdimitarg.profile.dto.UserRequestDto;
import com.ganchevdimitarg.profile.exception.InvalidRequestDataException;
import com.ganchevdimitarg.profile.service.ProfileServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.stream.Collectors;

import static com.ganchevdimitarg.profile.security.UserRole.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Testcontainers
@ExtendWith(MockitoExtension.class)
class CreateUserTest extends BaseTest {

    @Mock
    WebClient webClient;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    ReactiveCircuitBreaker circuitBreaker;
    @Mock
    ProfileDao profileDao;
    @InjectMocks
    ProfileServiceImpl profileService;

    @Test
    void createUser_happyPath_returnsUserDtoWithCardId() {
        // Arrange
        UserRequestDto request = new UserRequestDto("user@example.com", "Pass@1234",
                "Ivan", "Ivanov", "+359888000111", "Varna", "Main St", "9000",
                "4242424242424242", "03", "2026", "314");
        Profile savedProfile = Profile.builder()
                .id("1").username("user@example.com")
                .firstName("Ivan").lastName("Ivanov")
                .address(new Address("Varna", "Main St", "9000"))
                .phoneNumber("+359888000111")
                .grantedAuthorities(USER.getGrantedAuthorities()
                        .stream()
                        .map(SimpleGrantedAuthority::getAuthority)
                        .collect(Collectors.toSet()))
                .build();
        PaymentDto customerDto = PaymentDto.builder().customerId("cus_123").build();
        PaymentDto cardDto = PaymentDto.builder().customerId("cus_123").cardId("card_456").build();

        when(profileDao.findByUsername("user@example.com")).thenReturn(Mono.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(profileDao.insert(any(Profile.class))).thenReturn(Mono.just(savedProfile));
        when(webClient.post()).thenReturn(mock(WebClient.RequestBodyUriSpec.class));
        // stub payment calls to return customerDto then cardDto
        when(circuitBreaker.run(ArgumentMatchers.<Mono<Object>>any(), ArgumentMatchers.any()))
                .thenReturn(Mono.just(customerDto))
                .thenReturn(Mono.just(cardDto));

        // Act
        Mono<UserDto> result = profileService.createUser(request);

        // Assert
        StepVerifier.create(result)
                .assertNext(dto -> {
                    assertThat(dto.username()).isEqualTo("user@example.com");
                    assertThat(dto.cardId()).isEqualTo("card_456");
                })
                .verifyComplete();
    }

    @Test
    void createUser_paymentServiceDown_throwsInvalidRequestDataException() {
        // Arrange
        UserRequestDto request = new UserRequestDto("user@example.com", "Pass@1234",
                "Ivan", "Ivanov", "+359888000111", "Varna", "Main St", "9000",
                "4242424242424242", "03", "2026", "314");

        when(profileDao.findByUsername("user@example.com")).thenReturn(Mono.empty());
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(circuitBreaker.run(ArgumentMatchers.<Mono<Object>>any(), ArgumentMatchers.any()))
                .thenReturn(Mono.just(PaymentDto.builder().customerId("").build()));

        // Act
        Mono<UserDto> result = profileService.createUser(request);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof InvalidRequestDataException &&
                        ex.getMessage().contains("Payment service unavailable"))
                .verify();
    }
}