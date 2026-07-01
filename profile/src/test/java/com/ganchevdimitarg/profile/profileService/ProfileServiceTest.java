package com.ganchevdimitarg.profile.profileService;

import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Address;
import com.ganchevdimitarg.profile.domain.Profile;
import com.ganchevdimitarg.profile.dto.CardSetupCommand;
import com.ganchevdimitarg.profile.dto.PaymentDto;
import com.ganchevdimitarg.profile.dto.UpdateProfileCommand;
import com.ganchevdimitarg.profile.event.UserRegisteredEvent;
import com.ganchevdimitarg.profile.exception.InvalidRequestDataException;
import com.ganchevdimitarg.profile.property.PaymentServiceProperties;
import com.ganchevdimitarg.profile.service.ProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileServiceTest {

    private static final String USER_ID = UUID.randomUUID().toString();
    private static final String GHOST_ID = UUID.randomUUID().toString();

    @Mock ProfileDao profileDao;
    @Mock ReactiveCircuitBreaker circuitBreaker;
    @Mock ReactiveCircuitBreakerFactory<?, ?> circuitBreakerFactory;
    @Mock PaymentServiceProperties paymentProps;

    ProfileServiceImpl profileService;

    @BeforeEach
    void setUp() {
        WebClient webClient = WebClient.builder().build();
        when(circuitBreakerFactory.create(anyString())).thenReturn(circuitBreaker);

        profileService = new ProfileServiceImpl(webClient, profileDao, circuitBreakerFactory, paymentProps);

        when(paymentProps.customer()).thenReturn(
                new PaymentServiceProperties.CustomerUris("/cust-post", "/cust-get", "/cust-del"));
        when(paymentProps.card()).thenReturn(
                new PaymentServiceProperties.CardUris("/card-post", "/card-get"));
    }

    private static Profile profile(String userId) {
        return Profile.builder()
                .userId(userId)
                .firstName("Test").lastName("Testov")
                .address(new Address("Varna", "Main St", "9000"))
                .phoneNumber("0999999999")
                .created(LocalDateTime.now())
                .build();
    }

    private static UserRegisteredEvent registeredEvent(String userId) {
        return new UserRegisteredEvent(userId, "e@test.io", Set.of("ROLE_USER"),
                "Anna", "Smith", "888123456", "Sofia", "Main", "1000", "2026-06-26T00:00:00Z");
    }

    @Test
    void should_insertProfile_when_createProfileShellForNewUser() {
        when(profileDao.findByUserIdAndDeletedAtIsNull(USER_ID)).thenReturn(Mono.empty());
        when(profileDao.insert(any(Profile.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(profileService.createProfileShell(registeredEvent(USER_ID)))
                .verifyComplete();

        verify(profileDao).insert(ArgumentMatchers.<Profile>argThat(p ->
                p.getUserId().equals(USER_ID)
                        && p.getFirstName().equals("Anna")
                        && p.getAddress().city().equals("Sofia")));
    }

    @Test
    void should_beIdempotent_when_createProfileShellForExistingUser() {
        when(profileDao.findByUserIdAndDeletedAtIsNull(USER_ID)).thenReturn(Mono.just(profile(USER_ID)));

        StepVerifier.create(profileService.createProfileShell(registeredEvent(USER_ID)))
                .verifyComplete();

        verify(profileDao, never()).insert(any(Profile.class));
    }

    @Test
    void should_setDeletedAtAndCleanPayment_when_softDeleteProfile() {
        when(profileDao.findByUserIdAndDeletedAtIsNull(USER_ID)).thenReturn(Mono.just(profile(USER_ID)));
        when(profileDao.save(any(Profile.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(circuitBreaker.run(ArgumentMatchers.<Mono<String>>any(), any()))
                .thenReturn(Mono.just("ok"));

        StepVerifier.create(profileService.softDeleteProfile(USER_ID)).verifyComplete();

        verify(profileDao).save(ArgumentMatchers.<Profile>argThat(p -> p.getDeletedAt() != null));
    }

    @Test
    void should_stillSoftDelete_when_paymentCleanupFails() {
        when(profileDao.findByUserIdAndDeletedAtIsNull(USER_ID)).thenReturn(Mono.just(profile(USER_ID)));
        when(profileDao.save(any(Profile.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        // circuit breaker fallback yields an empty body → deletePaymentCustomer errors,
        // but soft-delete is authoritative and must still proceed.
        when(circuitBreaker.run(ArgumentMatchers.<Mono<String>>any(), any()))
                .thenReturn(Mono.just(""));

        StepVerifier.create(profileService.softDeleteProfile(USER_ID)).verifyComplete();

        verify(profileDao).save(ArgumentMatchers.<Profile>argThat(p -> p.getDeletedAt() != null));
    }

    @Test
    void should_throw_when_softDeleteProfileNotFound() {
        when(profileDao.findByUserIdAndDeletedAtIsNull(GHOST_ID)).thenReturn(Mono.empty());

        StepVerifier.create(profileService.softDeleteProfile(GHOST_ID))
                .expectErrorMatches(ex -> ex instanceof InvalidRequestDataException
                        && ex.getMessage().equals(ProfileServiceImpl.PROFILE_DOES_NOT_EXIST))
                .verify(Duration.ofSeconds(2));

        verify(profileDao, never()).save(any());
    }

    @Test
    void should_returnUserDtoWithCardId_when_getByUserId() {
        when(profileDao.findByUserIdAndDeletedAtIsNull(USER_ID)).thenReturn(Mono.just(profile(USER_ID)));
        when(circuitBreaker.run(ArgumentMatchers.<Mono<Set<String>>>any(), any()))
                .thenReturn(Mono.just(Set.of("card_123")));

        StepVerifier.create(profileService.getByUserId(USER_ID))
                .assertNext(dto -> {
                    assertThat(dto.userId()).isEqualTo(USER_ID);
                    assertThat(dto.cardId()).isEqualTo("card_123");
                })
                .verifyComplete();
    }

    @Test
    void should_returnEmptyCardId_when_getByUserIdAndPaymentDown() {
        when(profileDao.findByUserIdAndDeletedAtIsNull(USER_ID)).thenReturn(Mono.just(profile(USER_ID)));
        when(circuitBreaker.run(ArgumentMatchers.<Mono<Set<String>>>any(), any()))
                .thenReturn(Mono.just(Set.of("")));

        StepVerifier.create(profileService.getByUserId(USER_ID))
                .assertNext(dto -> assertThat(dto.cardId()).isEmpty())
                .verifyComplete();
    }

    @Test
    void should_throw_when_getByUserIdNotFound() {
        when(profileDao.findByUserIdAndDeletedAtIsNull(GHOST_ID)).thenReturn(Mono.empty());

        StepVerifier.create(profileService.getByUserId(GHOST_ID))
                .expectError(InvalidRequestDataException.class)
                .verify(Duration.ofSeconds(2));
    }

    @Test
    void should_saveUpdatedFields_when_updateProfile() {
        UpdateProfileCommand cmd = new UpdateProfileCommand(
                "Anna", "Smith", "Sofia", "Main", "1000", "0888777666");

        when(profileDao.findByUserIdAndDeletedAtIsNull(USER_ID)).thenReturn(Mono.just(profile(USER_ID)));
        when(profileDao.save(any(Profile.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(profileService.updateProfile(USER_ID, cmd)).verifyComplete();

        verify(profileDao).save(ArgumentMatchers.<Profile>argThat(p ->
                p.getFirstName().equals("Anna")
                        && p.getAddress().city().equals("Sofia")
                        && p.getPhoneNumber().equals("0888777666")));
    }

    @Test
    void should_throw_when_updateProfileNotFound() {
        UpdateProfileCommand cmd = new UpdateProfileCommand(
                "Anna", "Smith", "Sofia", "Main", "1000", "0888777666");
        when(profileDao.findByUserIdAndDeletedAtIsNull(GHOST_ID)).thenReturn(Mono.empty());

        StepVerifier.create(profileService.updateProfile(GHOST_ID, cmd))
                .expectError(InvalidRequestDataException.class)
                .verify(Duration.ofSeconds(2));

        verify(profileDao, never()).save(any());
    }

    @Test
    void should_returnUserDtoWithCardId_when_setupPayment() {
        CardSetupCommand cmd = new CardSetupCommand("4242424242424242", "03", "2026", "314");
        PaymentDto customer = PaymentDto.builder().customerId("cus_123").build();
        PaymentDto card = PaymentDto.builder().customerId("cus_123").cardId("card_456").build();

        when(profileDao.findByUserIdAndDeletedAtIsNull(USER_ID)).thenReturn(Mono.just(profile(USER_ID)));
        when(circuitBreaker.run(ArgumentMatchers.<Mono<Object>>any(), any()))
                .thenReturn(Mono.just(customer))
                .thenReturn(Mono.just(card));

        StepVerifier.create(profileService.setupPayment(USER_ID, cmd))
                .assertNext(dto -> {
                    assertThat(dto.userId()).isEqualTo(USER_ID);
                    assertThat(dto.cardId()).isEqualTo("card_456");
                })
                .verifyComplete();
    }

    @Test
    void should_throw_when_setupPaymentAndPaymentDown() {
        CardSetupCommand cmd = new CardSetupCommand("4242424242424242", "03", "2026", "314");

        when(profileDao.findByUserIdAndDeletedAtIsNull(USER_ID)).thenReturn(Mono.just(profile(USER_ID)));
        when(circuitBreaker.run(ArgumentMatchers.<Mono<Object>>any(), any()))
                .thenReturn(Mono.just(PaymentDto.builder().customerId("").build()));

        StepVerifier.create(profileService.setupPayment(USER_ID, cmd))
                .expectErrorMatches(ex -> ex instanceof InvalidRequestDataException
                        && ex.getMessage().contains("Payment service unavailable"))
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void should_throw_when_setupPaymentProfileNotFound() {
        CardSetupCommand cmd = new CardSetupCommand("4242424242424242", "03", "2026", "314");
        when(profileDao.findByUserIdAndDeletedAtIsNull(GHOST_ID)).thenReturn(Mono.empty());

        StepVerifier.create(profileService.setupPayment(GHOST_ID, cmd))
                .expectError(InvalidRequestDataException.class)
                .verify(Duration.ofSeconds(2));
    }
}
