package com.ganchevdimitarg.profile.service;

import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Address;
import com.ganchevdimitarg.profile.domain.Profile;
import com.ganchevdimitarg.profile.dto.CardDto;
import com.ganchevdimitarg.profile.dto.CardSetupCommand;
import com.ganchevdimitarg.profile.dto.PaymentDto;
import com.ganchevdimitarg.profile.dto.UpdateProfileCommand;
import com.ganchevdimitarg.profile.dto.UserDto;
import com.ganchevdimitarg.profile.event.UserRegisteredEvent;
import com.ganchevdimitarg.profile.exception.InvalidRequestDataException;
import com.ganchevdimitarg.profile.property.PaymentServiceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@Slf4j
public class ProfileServiceImpl implements ProfileService {

    public static final String PAYMENT_SERVICE_IS_DOWN = "Payment service is down";
    public static final String PROFILE_DOES_NOT_EXIST = "Profile does not exist";

    private final WebClient webClient;
    private final ProfileDao profileDao;
    private final ReactiveCircuitBreaker circuitBreaker;
    private final PaymentServiceProperties paymentProps;

    public ProfileServiceImpl(
            WebClient webClient,
            ProfileDao profileDao,
            ReactiveCircuitBreakerFactory<?, ?> reactiveCircuitBreakerFactory,
            PaymentServiceProperties paymentProps) {
        this.webClient = webClient;
        this.profileDao = profileDao;
        this.circuitBreaker = reactiveCircuitBreakerFactory.create("profileService");
        this.paymentProps = paymentProps;
    }

    /**
     * Creates a profile shell from a consumed {@link UserRegisteredEvent}.
     * Idempotent on {@code userId}: if an active profile already exists the
     * insert is skipped, so a redelivered event is a no-op.
     *
     * @param e the consumed registration event
     * @return a {@link Mono} completing empty once the shell exists
     */
    @Override
    public Mono<Void> createProfileShell(UserRegisteredEvent e) {
        return profileDao.findByUserIdAndDeletedAtIsNull(e.userId())
                .doOnNext(existing -> log.info("Profile shell already exists for userId {}", e.userId()))
                .switchIfEmpty(Mono.defer(() -> profileDao.insert(Profile.builder()
                                .userId(e.userId())
                                .firstName(e.firstName())
                                .lastName(e.lastName())
                                .phoneNumber(e.phoneNumber())
                                .address(new Address(e.city(), e.street(), e.postCode()))
                                .created(LocalDateTime.now())
                                .build())
                        .doOnSuccess(p -> log.info("Profile shell created for userId {}", e.userId()))))
                .then();
    }

    /**
     * Soft-deletes the active profile for the given userId and tears down the
     * associated payment customer. Payment cleanup runs first; the profile's
     * {@code deletedAt} is set only on success.
     *
     * @param userId the shared user identifier
     * @return a {@link Mono} completing empty on success
     * @throws InvalidRequestDataException if no active profile exists or payment is down
     */
    @Override
    public Mono<Void> softDeleteProfile(String userId) {
        return profileDao.findByUserIdAndDeletedAtIsNull(userId)
                .switchIfEmpty(Mono.error(new InvalidRequestDataException(PROFILE_DOES_NOT_EXIST)))
                .flatMap(profile -> deletePaymentCustomer(userId)
                        .then(Mono.defer(() -> {
                            profile.setDeletedAt(Instant.now());
                            return profileDao.save(profile);
                        })))
                .doOnSuccess(p -> log.info("Profile soft-deleted for userId {}", userId))
                .then();
    }

    /**
     * Retrieves the active profile for the given userId, enriched with the
     * payment card reference. Falls back to an empty card ID when the payment
     * service is unavailable.
     *
     * @param userId the shared user identifier
     * @return a {@link Mono} emitting the {@link UserDto}
     * @throws InvalidRequestDataException if no active profile exists
     */
    @Override
    public Mono<UserDto> getByUserId(String userId) {
        return profileDao.findByUserIdAndDeletedAtIsNull(userId)
                .switchIfEmpty(Mono.error(new InvalidRequestDataException(PROFILE_DOES_NOT_EXIST)))
                .flatMap(profile ->
                        webClient.get()
                                .uri(paymentProps.card().get() + userId)
                                .retrieve()
                                .bodyToMono(new ParameterizedTypeReference<Set<String>>() {})
                                .transform(it -> circuitBreaker.run(it, throwable -> {
                                    log.warn(PAYMENT_SERVICE_IS_DOWN, throwable);
                                    return Mono.just(Set.of(""));
                                }))
                                .map(cardIds -> getUserDto(profile, cardIds.stream().findFirst().orElse("")))
                );
    }

    /**
     * Updates the display fields of the active profile for the given userId.
     *
     * @param userId  the shared user identifier
     * @param command the new display values
     * @return a {@link Mono} completing empty on success
     * @throws InvalidRequestDataException if no active profile exists
     */
    @Override
    public Mono<Void> updateProfile(String userId, UpdateProfileCommand command) {
        return profileDao.findByUserIdAndDeletedAtIsNull(userId)
                .switchIfEmpty(Mono.error(new InvalidRequestDataException(PROFILE_DOES_NOT_EXIST)))
                .flatMap(profile -> {
                    profile.setFirstName(command.firstName());
                    profile.setLastName(command.lastName());
                    profile.setPhoneNumber(command.phoneNumber());
                    profile.setAddress(new Address(command.city(), command.street(), command.postCode()));
                    return profileDao.save(profile);
                })
                .doOnSuccess(p -> log.info("Profile updated for userId {}", userId))
                .then();
    }

    /**
     * Sets up a payment customer and attaches a card for the given userId,
     * returning the profile enriched with the new card reference.
     *
     * @param userId  the shared user identifier
     * @param command the card details
     * @return a {@link Mono} emitting the {@link UserDto} with the card ID
     * @throws InvalidRequestDataException if no active profile exists or payment is down
     */
    @Override
    public Mono<UserDto> setupPayment(String userId, CardSetupCommand command) {
        return profileDao.findByUserIdAndDeletedAtIsNull(userId)
                .switchIfEmpty(Mono.error(new InvalidRequestDataException(PROFILE_DOES_NOT_EXIST)))
                .flatMap(profile -> createPaymentCustomer(userId)
                        .flatMap(paymentCustomer -> {
                            log.info("Payment customer created: {}", paymentCustomer.customerId());
                            return addCardToCustomer(command, paymentCustomer.customerId());
                        })
                        .map(paymentDto -> {
                            log.info("Payment card {} added to customer", paymentDto.cardId());
                            return getUserDto(profile, paymentDto.cardId());
                        }))
                .doOnSuccess(u -> log.info("Payment set up for userId {}", userId));
    }

    private Mono<PaymentDto> createPaymentCustomer(String userId) {
        return sendRequestToPaymentService(
                paymentProps.customer().post(),
                PaymentDto.builder().username(userId).customerName(userId).build()
        );
    }

    private Mono<PaymentDto> addCardToCustomer(CardSetupCommand command, String customerId) {
        return sendRequestToPaymentService(
                paymentProps.card().post(),
                CardDto.builder()
                        .customerId(customerId)
                        .cardNumber(command.cardNumber())
                        .cardExpMonth(command.cardExpMonth())
                        .cardExpYear(command.cardExpYear())
                        .cardCvc(command.cardCvc())
                        .build()
        );
    }

    private Mono<PaymentDto> sendRequestToPaymentService(String uri, Object body) {
        return webClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(PaymentDto.class)
                .transform(it -> circuitBreaker.run(it, throwable -> {
                    log.warn(PAYMENT_SERVICE_IS_DOWN, throwable);
                    return Mono.just(PaymentDto.builder().customerId("").build());
                }))
                .switchIfEmpty(Mono.error(
                        new InvalidRequestDataException("Payment service returned empty response")))
                .flatMap(dto -> {
                    if (dto.customerId() == null || dto.customerId().isEmpty()) {
                        return Mono.error(new InvalidRequestDataException(
                                "Payment service unavailable. Please check request details."));
                    }
                    return Mono.just(dto);
                });
    }

    private Mono<Void> deletePaymentCustomer(String userId) {
        return webClient.delete()
                .uri(paymentProps.customer().delete() + userId)
                .retrieve()
                .bodyToMono(String.class)
                .transform(it -> circuitBreaker.run(it, throwable -> {
                    log.warn(PAYMENT_SERVICE_IS_DOWN, throwable);
                    return Mono.just("");
                }))
                .flatMap(result -> {
                    if (result.isEmpty()) {
                        return Mono.error(new InvalidRequestDataException(
                                "Payment service unavailable. Please check request details."));
                    }
                    return Mono.just(result);
                })
                .then();
    }

    private UserDto getUserDto(Profile profile, String cardId) {
        return UserDto.builder()
                .userId(profile.getUserId())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .phoneNumber(profile.getPhoneNumber())
                .city(profile.getAddress().city())
                .street(profile.getAddress().street())
                .postCode(profile.getAddress().postCode())
                .cardId(cardId)
                .build();
    }
}
