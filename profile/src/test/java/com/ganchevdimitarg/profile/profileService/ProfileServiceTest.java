package com.ganchevdimitarg.profile.profileService;

import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Address;
import com.ganchevdimitarg.profile.domain.Profile;
import com.ganchevdimitarg.profile.dto.PaymentDto;
import com.ganchevdimitarg.profile.dto.UserDto;
import com.ganchevdimitarg.profile.dto.UserRequestDto;
import com.ganchevdimitarg.profile.exception.InvalidRequestDataException;
import com.ganchevdimitarg.profile.property.PaymentServiceProperties;
import com.ganchevdimitarg.profile.security.UserRole;
import com.ganchevdimitarg.profile.service.JwtService;
import com.ganchevdimitarg.profile.service.MailService;
import com.ganchevdimitarg.profile.service.ProfileServiceImpl;
import io.jsonwebtoken.JwtException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ganchevdimitarg.profile.security.UserRole.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileServiceTest {

    public static final String ADMIN_USERNAME = "admin@example.com";
    public static final String USER_USERNAME = "user@example.com";
    public static final String WORKER_USERNAME = "worker@example.com";
    public static final String GHOST_USERNAME = "ghost@example.com";
    public static final String OLD_USERNAME = "old@example.com";

    @Mock
    ProfileDao profileDao;
    @Mock
    JwtService jwtService;
    @Mock
    MailService mailService;
    @Mock
    ReactiveCircuitBreaker circuitBreaker;
    @Mock
    ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory;
    @Mock
    PaymentServiceProperties paymentProps;
    @Mock
    PasswordEncoder passwordEncoder;

    MockWebServer mockWebServer;
    ProfileServiceImpl profileService;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        when(circuitBreakerFactory.create(anyString())).thenReturn(circuitBreaker);

        profileService = new ProfileServiceImpl(
                webClient, profileDao, jwtService, mailService,
                circuitBreakerFactory, paymentProps
        );

        when(circuitBreaker.run(ArgumentMatchers.<Mono<Set<String>>>any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(paymentProps.customer()).thenReturn(
                new PaymentServiceProperties.CustomerUris("/cust-post", "/cust-get", "/cust-del")
        );

        when(paymentProps.card()).thenReturn(
                new PaymentServiceProperties.CardUris("/card-post", "/card-get")
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    private static Profile getSavedProfile(String username, UserRole role) {
        return Profile.builder()
                .id("1")
                .username(username)
                .grantedAuthorities(role.getGrantedAuthorities()
                        .stream()
                        .map(SimpleGrantedAuthority::getAuthority)
                        .collect(Collectors.toSet())
                )
                .firstName("Test")
                .lastName("Testov")
                .address(new Address("Varna", "Main St", "9000"))
                .phoneNumber("+359888000111")
                .build();
    }

    private static @NonNull UserRequestDto getRequestUser(String email, boolean hasCard) {
        return new UserRequestDto(
                email,
                "Pass@1234",
                "Test",
                "Testov",
                "Varna",
                "Main St",
                "9000",
                "+359888000111",
                hasCard ? "4242424242424242" : null,
                hasCard ? "03" : null,
                hasCard ? "2026" : null,
                hasCard ? "314" : null
        );
    }

    @Test
    void createAdmin_happyPath_returnsUserDto() {
        // Arrange
        UserRequestDto request = getRequestUser(ADMIN_USERNAME, false);
        Profile savedProfile = getSavedProfile(ADMIN_USERNAME, ADMIN);

        when(profileDao.findByUsername(ADMIN_USERNAME)).thenReturn(Mono.empty());
        when(profileDao.insert(any(Profile.class))).thenReturn(Mono.just(savedProfile));

        // Act
        Mono<UserDto> result = profileService.createAdmin(request);

        // Assert
        StepVerifier.create(result)
                .assertNext(dto -> {
                    assertThat(dto.username()).isEqualTo(ADMIN_USERNAME);
                    assertThat(dto.firstName()).isEqualTo("Test");
                    assertThat(dto.cardId()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void createAdmin_duplicateUsername_throwsInvalidRequestDataException() {
        // Arrange
        UserRequestDto request = getRequestUser(ADMIN_USERNAME, false);

        when(profileDao.findByUsername(ADMIN_USERNAME))
                .thenReturn(Mono.just(Profile.builder().build()));

        // Act
        Mono<UserDto> result = profileService.createAdmin(request);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof InvalidRequestDataException &&
                        ex.getMessage().contains("Profile already exists"))
                .verify();
    }

    @Test
    void createUser_happyPath_returnsUserDtoWithCardId() {
        // Arrange
        UserRequestDto request = getRequestUser(USER_USERNAME, true);
        Profile savedProfile = getSavedProfile(USER_USERNAME, USER);

        PaymentDto customerDto = PaymentDto.builder().customerId("cus_123").build();
        PaymentDto cardDto = PaymentDto.builder().customerId("cus_123").cardId("card_456").build();

        when(profileDao.findByUsername(USER_USERNAME)).thenReturn(Mono.empty());
        when(profileDao.insert(any(Profile.class))).thenReturn(Mono.just(savedProfile));
        when(circuitBreaker.run(ArgumentMatchers.<Mono<Object>>any(), ArgumentMatchers.any()))
                .thenReturn(Mono.just(customerDto))
                .thenReturn(Mono.just(cardDto));

        // Act
        Mono<UserDto> result = profileService.createUser(request);

        // Assert
        StepVerifier.create(result)
                .assertNext(dto -> {
                    assertThat(dto.username()).isEqualTo(USER_USERNAME);
                    assertThat(dto.cardId()).isEqualTo("card_456");
                })
                .verifyComplete();
    }

    @Test
    void createUser_paymentServiceDown_throwsInvalidRequestDataException() {
        // Arrange
        UserRequestDto request = getRequestUser(USER_USERNAME, true);

        when(profileDao.findByUsername(USER_USERNAME)).thenReturn(Mono.empty());
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

    @Test
    void createWorker_happyPath_returnsUserDtoWithWorkerAuthorities() {
        // Arrange
        UserRequestDto request = getRequestUser(WORKER_USERNAME, true);
        Profile savedProfile = getSavedProfile(WORKER_USERNAME, WORKER);

        when(profileDao.findByUsername(WORKER_USERNAME)).thenReturn(Mono.empty());
        when(profileDao.insert(any(Profile.class))).thenReturn(Mono.just(savedProfile));

        // Act
        Mono<UserDto> result = profileService.createWorker(request);

        // Assert
        StepVerifier.create(result)
                .assertNext(dto -> {
                    assertThat(dto.username()).isEqualTo(WORKER_USERNAME);
                    assertThat(dto.cardId()).isEmpty();
                    assertThat(dto.grantedAuthorities())
                            .containsAll(WORKER.getGrantedAuthorities()
                                    .stream()
                                    .map(SimpleGrantedAuthority::getAuthority)
                                    .collect(Collectors.toSet())
                            );
                })
                .verifyComplete();
    }

    @Test
    void createWorker_duplicateUsername_throwsInvalidRequestDataException() {
        // Arrange
        UserRequestDto request = getRequestUser(WORKER_USERNAME, false);
        when(profileDao.findByUsername(WORKER_USERNAME))
                .thenReturn(Mono.just(Profile.builder().build()));

        // Act
        Mono<UserDto> result = profileService.createWorker(request);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof InvalidRequestDataException &&
                        ex.getMessage().contains("Profile already exists"))
                .verify();
    }

    @Test
    void deleteUser_happyPath_deletesProfileAndPaymentCustomer() {
        // Arrange
        Profile saveProfile = getSavedProfile(USER_USERNAME, USER);

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(204));

        when(profileDao.findByUsername(USER_USERNAME)).thenReturn(Mono.just(saveProfile));
        when(profileDao.delete(saveProfile)).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = profileService.deleteUser(USER_USERNAME);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        // Verify
        verify(profileDao).delete(saveProfile);
    }

    @Test
    void deleteUser_profileNotFound_throwsUsernameNotFoundException() {
        // Arrange
        when(profileDao.findByUsername(GHOST_USERNAME)).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = profileService.deleteUser(GHOST_USERNAME);

        // Assert
        StepVerifier.create(result)
                .expectError(UsernameNotFoundException.class)
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void deleteUser_paymentServiceDown_throwsInvalidRequestDataException() {
        // Arrange
        Profile saveProfile = getSavedProfile(USER_USERNAME, USER);

        when(profileDao.findByUsername(USER_USERNAME)).thenReturn(Mono.just(saveProfile));
        when(profileDao.delete(any())).thenReturn(Mono.empty());
        when(circuitBreaker.run(any(Mono.class), any())).thenReturn(Mono.just(""));

        // Act
        Mono<Void> result = profileService.deleteUser(USER_USERNAME);

        // Assert
        StepVerifier.create(result)
                .expectError(InvalidRequestDataException.class)
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void getUserByUsername_happyPath_returnsUserDtoWithCardId() {
        // Arrange
        Profile saveProfile = getSavedProfile(USER_USERNAME, USER);
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("[\"card_123\"]"));

        when(profileDao.findByUsername(USER_USERNAME)).thenReturn(Mono.just(saveProfile));

        // Act
        Mono<UserDto> result = profileService.getUserByUsername(USER_USERNAME);

        // Assert
        StepVerifier.create(result)
                .assertNext(dto -> {
                    assertThat(dto.username()).isEqualTo(USER_USERNAME);
                    assertThat(dto.cardId()).isEqualTo("card_123");
                })
                .verifyComplete();
    }

    @Test
    void getUserByUsername_paymentServiceDown_returnsUserDtoWithEmptyCardId() {
        // Arrange
        Profile saveProfile = getSavedProfile(USER_USERNAME, USER);
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        when(profileDao.findByUsername(USER_USERNAME)).thenReturn(Mono.just(saveProfile));
        when(circuitBreaker.run(ArgumentMatchers.<Mono<Set<String>>>any(), any()))
                .thenReturn(Mono.just(Set.of("")));

        // Act
        Mono<UserDto> result = profileService.getUserByUsername(USER_USERNAME);

        // Assert
        StepVerifier.create(result)
                .assertNext(dto -> {
                    assertThat(dto.username()).isEqualTo(USER_USERNAME);
                    assertThat(dto.cardId()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void getUserByUsername_profileNotFound_throwsUsernameNotFoundException() {
        // Arrange
        when(profileDao.findByUsername(GHOST_USERNAME)).thenReturn(Mono.empty());

        // Act
        Mono<UserDto> result = profileService.getUserByUsername(GHOST_USERNAME);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof UsernameNotFoundException &&
                        ex.getMessage().equals(ProfileServiceImpl.PROFILE_DOES_NOT_EXIST))
                .verify(Duration.ofSeconds(2));
    }

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

    @Test
    void passwordReset_happyPath_sendsEmailAndCompletes() {
        // Arrange
        String token = "reset-token-xyz";
        Profile saveProfile = getSavedProfile(USER_USERNAME, USER);

        when(profileDao.findByUsername(USER_USERNAME)).thenReturn(Mono.just(saveProfile));
        when(jwtService.generateToken(any())).thenReturn(Mono.just(token));
        // Уверете се, че mailService връща Mono.empty(), а не null!
        when(mailService.sendPasswordResetTokenMail(USER_USERNAME, token)).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = profileService.passwordReset(USER_USERNAME);

        // Assert
        StepVerifier.create(result)
                .expectSubscription()
                .verifyComplete();

        // Verify, че веригата е минала през всички стъпки
        verify(profileDao).findByUsername(USER_USERNAME);
        verify(jwtService).generateToken(any());
        verify(mailService).sendPasswordResetTokenMail(USER_USERNAME, token);
    }

    @Test
    void passwordReset_userNotFound_throwsInvalidRequestDataException() {
        // Arrange
        when(profileDao.findByUsername(GHOST_USERNAME)).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = profileService.passwordReset(GHOST_USERNAME);

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof InvalidRequestDataException &&
                        ex.getMessage().equals("User does not exist"))
                .verify(Duration.ofSeconds(2));

        // Уверяваме се, че не генерираме токени и не пращаме мейли за несъществуващи хора
        verifyNoInteractions(jwtService);
        verifyNoInteractions(mailService);
    }

    @Test
    @Disabled
    void setNewPassword_happyPath_encodesAndSavesPassword() {
        // Arrange
        String rawPassword = "NewPass@1";
        String encodedPassword = "encodedNewPass";
        Profile saveProfile = getSavedProfile(USER_USERNAME, USER);
        saveProfile.setPassword("oldPass");

        // ФИКС: Трябва да кажете на Mock-а какво да върне
        when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);
        when(profileDao.findByUsername(USER_USERNAME)).thenReturn(Mono.just(saveProfile));
        when(profileDao.save(any(Profile.class))).thenReturn(Mono.just(saveProfile));

        // Act
        Mono<Void> result = profileService.setNewPassword(USER_USERNAME, rawPassword);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        // Сега проверката ще мине, защото password ще е "encodedNewPass"
        verify(profileDao).save(argThat(p -> p.getPassword().equals(encodedPassword)));
    }

    @Test
    void setNewPassword_userNotFound_throwsInvalidRequestDataException() {
        // Arrange
        when(profileDao.findByUsername(GHOST_USERNAME)).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = profileService.setNewPassword(GHOST_USERNAME, "AnyPass@1");

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof InvalidRequestDataException &&
                        ex.getMessage().equals("User does not exist"))
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void updateUser_happyPath_savesUpdatedProfile() {
        // Arrange
        Profile saveProfile = getSavedProfile(OLD_USERNAME, USER);
        UserRequestDto request = getRequestUser(USER_USERNAME, false);

        when(profileDao.findByUsername(OLD_USERNAME)).thenReturn(Mono.just(saveProfile));
        when(profileDao.save(any(Profile.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // Act
        Mono<Void> result = profileService.updateUser(OLD_USERNAME, request);

        // Assert
        StepVerifier.create(result)
                .expectSubscription()
                .verifyComplete();

        verify(profileDao).save(argThat(p ->
                p.getUsername().equals(USER_USERNAME) &&
                        p.getFirstName().equals("Test") &&
                        p.getAddress().city().equals("Varna") &&
                        p.getAddress().postCode().equals("9000")
        ));
    }

    @Test
    void updateUser_profileNotFound_throwsUsernameNotFoundException() {
        // Arrange
        UserRequestDto request = getRequestUser(GHOST_USERNAME, false);

        when(profileDao.findByUsername(GHOST_USERNAME)).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = profileService.updateUser(GHOST_USERNAME, request);

        // Assert
        StepVerifier.create(result)
                .expectError(UsernameNotFoundException.class)
                .verify(Duration.ofSeconds(2));

        verify(profileDao, never()).save(any());
    }

}