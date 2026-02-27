package com.ganchevdimitarg.profile.service;

import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Address;
import com.ganchevdimitarg.profile.domain.Profile;
import com.ganchevdimitarg.profile.dto.CardDto;
import com.ganchevdimitarg.profile.dto.PaymentDto;
import com.ganchevdimitarg.profile.dto.UserDto;
import com.ganchevdimitarg.profile.dto.UserRequestDto;
import com.ganchevdimitarg.profile.exception.InvalidRequestDataException;
import com.ganchevdimitarg.profile.property.PaymentServiceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ganchevdimitarg.profile.security.UserRole.*;

@Service
@Slf4j
public class ProfileServiceImpl implements ProfileService {

    public static final String PAYMENT_SERVICE_IS_DOWN = "Payment service is down";
    public static final String PROFILE_DOES_NOT_EXIST = "Profile does not exist";

    private final WebClient webClient;
    private final ProfileDao profileDao;
    private final JwtService jwtService;
    private final MailService mailService;
    private final ReactiveCircuitBreaker circuitBreaker;
    private final PaymentServiceProperties paymentProps;

    public ProfileServiceImpl(
            WebClient webClient,
            ProfileDao profileDao,
            JwtService jwtService,
            MailService mailService,
            ReactiveCircuitBreakerFactory<?, ?> reactiveCircuitBreakerFactory, PaymentServiceProperties paymentProps) {
        this.webClient = webClient;
        this.profileDao = profileDao;
        this.jwtService = jwtService;
        this.mailService = mailService;
        this.circuitBreaker = reactiveCircuitBreakerFactory.create("profileService");
        this.paymentProps = paymentProps;
    }

    /**
     * Creates a new admin user profile with full system permissions.
     *
     * @param userRequestDto the request payload containing admin profile details
     * @return a {@link Mono} emitting the created {@link UserDto}, never empty
     * @throws InvalidRequestDataException if a profile with the same username already exists
     */
    @Override
    public Mono<UserDto> createAdmin(UserRequestDto userRequestDto) {
        return createStaff(userRequestDto, ADMIN.getGrantedAuthorities())
                .map(profile -> getUserDto(profile, ""))
                .doOnSuccess(u -> log.info("Admin user with username: {} was created", u.username()));
    }

    /**
     * Creates a new worker user profile with read-only permissions
     * across catalog, profile, order, and notification domains.
     *
     * @param userRequestDto the request payload containing worker profile details
     * @return a {@link Mono} emitting the created {@link UserDto}, never empty
     * @throws InvalidRequestDataException if a profile with the same username already exists
     */
    @Override
    public Mono<UserDto> createWorker(UserRequestDto userRequestDto) {
        return createStaff(userRequestDto, WORKER.getGrantedAuthorities())
                .map(profile -> getUserDto(profile, ""))
                .doOnSuccess(u -> log.info("Worker user with username: {} was created", u.username()));
    }

    /**
     * Creates a new regular user profile and sets up their payment customer and card.
     * Profile insertion only occurs after both payment customer and card are created successfully.
     *
     * @param userRequestDto the request payload containing user profile and card details
     * @return a {@link Mono} emitting the created {@link UserDto} with card ID, never empty
     * @throws InvalidRequestDataException if a profile already exists or payment service is unavailable
     */
    @Override
    public Mono<UserDto> createUser(UserRequestDto userRequestDto) {
        return buildProfile(userRequestDto, USER.getGrantedAuthorities())
                .flatMap(authProfile ->
                        createPaymentCustomer(userRequestDto.username())
                                .flatMap(paymentCustomer -> {
                                    log.info("Payment customer created: {}", paymentCustomer.customerId());
                                    return addCardToCustomer(userRequestDto, paymentCustomer.customerId());
                                })
                                .flatMap(paymentDto -> {
                                    log.info("Payment card {} added to customer", paymentDto.cardId());
                                    return profileDao.insert(authProfile)
                                            .map(saved -> getUserDto(saved, paymentDto.cardId()));
                                })
                )
                .doOnSuccess(u -> log.info("User with username: {} was created", u.username()));
    }

    /**
     * Updates an existing user profile with new details from the request payload.
     *
     * @param username       the username identifying the profile to update
     * @param userRequestDto the request payload containing updated profile fields
     * @return a {@link Mono} completing empty on success
     * @throws UsernameNotFoundException if no profile exists for the given username
     */
    @Override
    public Mono<Void> updateUser(String username, UserRequestDto userRequestDto) {
        return profileDao.findByUsername(username)
                .switchIfEmpty(Mono.error(new UsernameNotFoundException(PROFILE_DOES_NOT_EXIST)))
                .flatMap(profile -> {
                    profile.setUsername(userRequestDto.username());
//                    profile.setPassword(passwordEncoder.encode(userRequestDto.password()));
                    profile.setFirstName(userRequestDto.firstName());
                    profile.setLastName(userRequestDto.lastName());
                    profile.setPhoneNumber(userRequestDto.phoneNumber());
                    profile.setAddress(new Address(
                            userRequestDto.city(),
                            userRequestDto.street(),
                            userRequestDto.postCode()));
                    return profileDao.save(profile);
                })
                .doOnSuccess(p -> log.info("Profile with username {} updated", p.getUsername()))
                .then();
    }

    /**
     * Deletes an existing user profile and their associated payment customer record.
     * Payment customer deletion is attempted first; profile deletion only proceeds on success.
     *
     * @param username the username identifying the profile to delete
     * @return a {@link Mono} completing empty on success
     * @throws UsernameNotFoundException   if no profile exists for the given username
     * @throws InvalidRequestDataException if the payment service is unavailable
     */
    @Override
    public Mono<Void> deleteUser(String username) {
        return profileDao.findByUsername(username)
                .switchIfEmpty(Mono.error(new UsernameNotFoundException(PROFILE_DOES_NOT_EXIST)))
                .flatMap(profile ->
                        deletePaymentCustomer(username)
                                .then(profileDao.delete(profile))
                )
                .doOnSuccess(v -> log.info("User with username: {} was deleted", username));
    }

    /**
     * Retrieves a user profile by username, enriched with their payment card ID
     * fetched from the payment service. Falls back to an empty card ID if the
     * payment service is unavailable.
     *
     * @param username the username identifying the profile to retrieve
     * @return a {@link Mono} emitting the {@link UserDto} enriched with card data
     * @throws UsernameNotFoundException if no profile exists for the given username
     */
    @Override
    public Mono<UserDto> getUserByUsername(String username) {
        return profileDao.findByUsername(username)
                .switchIfEmpty(Mono.error(new UsernameNotFoundException(PROFILE_DOES_NOT_EXIST)))
                .flatMap(profile ->
                        webClient.get()
                                .uri(paymentProps.card().get() + username)
                                .retrieve()
                                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Set<String>>() {})
                                .transform(it -> circuitBreaker.run(it, throwable -> {
                                    log.warn(PAYMENT_SERVICE_IS_DOWN, throwable);
                                    return Mono.just(Set.of(""));
                                }))
                                .map(cardIds -> getUserDto(profile, cardIds.stream().findFirst().orElse("")))
                );
    }

    /**
     * Generates a password reset JWT token for the given user and sends it via email.
     * The token is never returned to the caller — it is delivered exclusively via email.
     *
     * @param username the username of the user requesting a password reset
     * @return a {@link Mono} completing empty after the email is dispatched
     * @throws InvalidRequestDataException if no profile exists for the given username
     */
    @Override
    public Mono<Void> passwordReset(String username) {
        return profileDao.findByUsername(username)
                .switchIfEmpty(Mono.error(new InvalidRequestDataException("User does not exist")))
                .flatMap(profile -> jwtService.generateToken(
                        new User(username, "", USER.getGrantedAuthorities())
                ))
                .flatMap(token -> mailService.sendPasswordResetTokenMail(username, token))
                .doOnSuccess(v -> log.info("Password reset token sent for user {}", username));
    }

    /**
     * Delegates JWT password reset token validation to {@link JwtService}.
     *
     * @param token the compact JWT string to validate
     * @return a {@link Mono} emitting {@code true} if the token is valid, {@code false} otherwise
     */
    @Override
    public Mono<Boolean> isPasswordResetTokenValid(String token) {
        return jwtService.isTokenValid(token);
    }

    /**
     * Sets a new encoded password for the specified user profile.
     *
     * @param username the username identifying the profile to update
     * @param password the new raw password to encode and persist
     * @return a {@link Mono} completing empty on success
     * @throws InvalidRequestDataException if no profile exists for the given username
     */
    @Override
    public Mono<Void> setNewPassword(String username, String password) {
        //                    profile.setPassword(passwordEncoder.encode(password));
        return profileDao.findByUsername(username)
                .switchIfEmpty(Mono.error(new InvalidRequestDataException("User does not exist")))
                .flatMap(profileDao::save)
                .doOnSuccess(p -> log.info("Password changed for user {}", username))
                .then();
    }

    private Mono<Profile> createStaff(UserRequestDto userRequestDto,
                                      Set<SimpleGrantedAuthority> grantedAuthorities) {
        return buildProfile(userRequestDto, grantedAuthorities)
                .flatMap(profileDao::insert)
                .doOnSuccess(p -> log.info("Profile was successfully created"));
    }

    private Mono<Profile> buildProfile(UserRequestDto model,
                                       Set<SimpleGrantedAuthority> grantedAuthorities) {
        return profileDao.findByUsername(model.username())
                .flatMap(existing -> Mono.<Profile>error(
                        new InvalidRequestDataException(
                                String.format("Profile already exists: %s", model.username()))))
                .switchIfEmpty(Mono.fromCallable(() ->
                        Profile.builder()
                                .username(model.username())
//                                .password(passwordEncoder.encode(model.password().trim()))
                                .grantedAuthorities(grantedAuthorities.stream()
                                        .map(SimpleGrantedAuthority::getAuthority)
                                        .collect(Collectors.toSet()))
                                .firstName(model.firstName())
                                .lastName(model.lastName())
                                .address(new Address(model.city(), model.street(), model.postCode()))
                                .phoneNumber(model.phoneNumber())
                                .created(LocalDateTime.now())
                                .build()
                ));
    }

    private Mono<PaymentDto> createPaymentCustomer(String username) {
        return sendRequestToPaymentService(
                paymentProps.customer().post(),
                PaymentDto.builder().username(username).customerName(username).build()
        );
    }

    private Mono<PaymentDto> addCardToCustomer(UserRequestDto userRequestDto, String customerId) {
        return sendRequestToPaymentService(
                paymentProps.card().post(),
                CardDto.builder()
                        .customerId(customerId)
                        .cardNumber(userRequestDto.cardNumber())
                        .cardExpMonth(userRequestDto.cardExpMonth())
                        .cardExpYear(userRequestDto.cardExpYear())
                        .cardCvc(userRequestDto.cardCvc())
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

    private Mono<Void> deletePaymentCustomer(String username) {
        return webClient.delete()
                .uri(paymentProps.customer().delete() + username)
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
                .id(profile.getId())
                .username(profile.getUsername())
                .password("")
                .grantedAuthorities(profile.getGrantedAuthorities())
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