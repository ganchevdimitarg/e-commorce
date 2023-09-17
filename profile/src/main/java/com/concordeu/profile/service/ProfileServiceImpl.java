package com.concordeu.profile.service;

import com.concordeu.profile.dto.CardDto;
import com.concordeu.profile.dto.PaymentDto;
import com.concordeu.profile.dto.UserDto;
import com.concordeu.profile.dto.UserRequestDto;
import com.concordeu.profile.entities.Address;
import com.concordeu.profile.entities.Profile;
import com.concordeu.profile.excaption.InvalidRequestDataException;
import com.concordeu.profile.repositories.ProfileRepository;
import com.concordeu.profile.validation.ValidateData;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.concordeu.profile.security.UserRole.*;


@Service
@Slf4j
public class ProfileServiceImpl implements ProfileService {
    private final PasswordEncoder passwordEncoder;
    private final WebClient webClient;
    private final Gson mapper;
    private final ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory;
    private final ProfileRepository profileRepository;
    private final JwtService jwtService;
    private final MailService mailService;
    private final ValidateData validateData;
    private final String paymentServiceCreateNewCustomerUri;
    private final String paymentServiceDeleteCustomerByUsernameUri;
    private final String paymentServiceCreateCardUri;
    private final String paymentServiceGetCardsByUsernameUri;

    public ProfileServiceImpl(PasswordEncoder passwordEncoder,
                              WebClient.Builder webClientBuilder,
                              Gson mapper,
                              ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory,
                              ProfileRepository profileRepository,
                              JwtService jwtService,
                              MailService mailService,
                              ValidateData validateData,
                              @Value("${payment.service.customer.post.uri}") String paymentServiceCreateNewCustomerUri,
                              @Value("${payment.service.customer.delete.uri}") String paymentServiceDeleteCustomerByUsernameUri,
                              @Value("${payment.service.card.post.uri}") String paymentServiceCreateCardUri,
                              @Value("${payment.service.card.get.uri}") String paymentServiceGetCardsByUsernameUri) {
        this.passwordEncoder = passwordEncoder;
        this.webClient = webClientBuilder.build();
        this.mapper = mapper;
        this.reactiveCircuitBreakerFactory = reactiveCircuitBreakerFactory;
        this.profileRepository = profileRepository;
        this.jwtService = jwtService;
        this.mailService = mailService;
        this.validateData = validateData;
        this.paymentServiceCreateNewCustomerUri = paymentServiceCreateNewCustomerUri;
        this.paymentServiceDeleteCustomerByUsernameUri = paymentServiceDeleteCustomerByUsernameUri;
        this.paymentServiceCreateCardUri = paymentServiceCreateCardUri;
        this.paymentServiceGetCardsByUsernameUri = paymentServiceGetCardsByUsernameUri;
    }


    @Override
    public Mono<UserDto> createAdmin(UserRequestDto userRequestDto) {
        UserDto userDto = getUserDto(createStaff(userRequestDto, ADMIN.getGrantedAuthorities()), "");
        log.info("Admin user with username: {} was created", userDto.username());
        return Mono.just(userDto);
    }

    @Override
    public Mono<UserDto> createWorker(UserRequestDto userRequestDto) {
        UserDto userDto = getUserDto(createStaff(userRequestDto, WORKER.getGrantedAuthorities()), "");
        log.info("Worker user with username: {} was created", userDto.username());
        return Mono.just(userDto);
    }

    @Override
    public Mono<UserDto> createUser(UserRequestDto userRequestDto) {
        UserDto userDto = createUser(userRequestDto, USER.getGrantedAuthorities());
        log.info("Profile user with username: {} was created", userDto.username());
        return Mono.just(userDto);
    }

    @Override
    public void updateUser(String username,
                           UserRequestDto userRequestDto) {
        Assert.hasLength(username, "Username is empty");

        Profile profile = profileRepository.findByUsername(username)
                .blockOptional()
                .orElseThrow(() -> new UsernameNotFoundException("Profile does not exist"));
        Address address = new Address(
                userRequestDto.city(),
                userRequestDto.street(),
                userRequestDto.postCode());

        profile.setUsername(userRequestDto.username());
        profile.setPassword(passwordEncoder.encode(userRequestDto.password()));
        profile.setFirstName(userRequestDto.firstName());
        profile.setLastName(userRequestDto.lastName());
        profile.setPhoneNumber(userRequestDto.phoneNumber());
        profile.setAddress(address);

        profileRepository.save(profile).subscribe();
        log.info("Profile with username {} is update", profile.getUsername());
    }

    @Override
    public void deleteUser(String username) {
        Assert.hasLength(username, "Username is empty");
        Profile profile = profileRepository.findByUsername(username)
                .blockOptional()
                .orElseThrow(() -> {
                    log.warn("Profile does not exist");
                    return new UsernameNotFoundException("Profile does not exist");
                });

        deletePaymentCustomer(username);

        profileRepository.delete(profile).subscribe();
        log.info("User with username: {} was successfully deleted", username);
    }

    @Override
    public Mono<UserDto> getUserByUsername(String username) {
        Assert.hasLength(username, "Username is empty");

        Profile profile = profileRepository.findByUsername(username)
                .blockOptional()
                .orElseThrow(() -> new UsernameNotFoundException("Profile does not exist"));

        List<String> paymentCustomerId = new LinkedList<>();

        webClient
                .get()
                .uri(paymentServiceGetCardsByUsernameUri + username)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Set<String>>() {
                })
                .transform(it ->
                        reactiveCircuitBreakerFactory.create("profileService")
                                .run(it, throwable -> {
                                    log.warn("Payment service is down: " + throwable.getMessage());
                                    return Mono.just(Set.of(""));
                                })
                )
                .subscribe(p -> paymentCustomerId.add(p.stream().findFirst().get()));


        return Mono.just(getUserDto(Mono.just(profile), paymentCustomerId.get(0).isEmpty() ? "N/A" : paymentCustomerId.get(0)));
    }

    @Override
    public Mono<String> passwordReset(String username) {
        profileRepository.findByUsername(username)
                .blockOptional()
                .orElseThrow(() -> new InvalidRequestDataException("User does not exist"));
        String token = jwtService.generateToken(
                new User(
                        username,
                        "",
                        USER.getGrantedAuthorities()
                )
        );
        mailService.sendPasswordResetTokenMail(username, token);
        log.info("Successfully generated password reset token");
        return Mono.just(token);
    }

    @Override
    public boolean isPasswordResetTokenValid(String token) {
        return jwtService.isTokenValid(token);
    }

    @Override
    public void setNewPassword(String username,
                               String password) {
        Profile profile = profileRepository.findByUsername(username)
                .blockOptional()
                .orElseThrow(() -> new InvalidRequestDataException("User does not exist"));
        validateData.isValidPassword(password);
        Objects.requireNonNull(profile).setPassword(passwordEncoder.encode(password));
        profileRepository.save(profile).subscribe();
        log.info("The password of user {} has been changed successfully", username);
    }

    private UserDto getUserDto(Mono<Profile> profile, String cardId) {
        return UserDto.builder()
                .id(profile.map(Profile::getId).toString())
                .username(profile.map(Profile::getUsername).toString())
                .password("")
                .grantedAuthorities((Set<SimpleGrantedAuthority>) profile.map(Profile::getGrantedAuthorities).subscribe())
                .firstName(profile.map(Profile::getFirstName).toString())
                .lastName(profile.map(Profile::getLastName).toString())
                .phoneNumber(profile.map(Profile::getPhoneNumber).toString())
                .city(profile.map(p -> p.getAddress().city()).toString())
                .street(profile.map(p -> p.getAddress().street()).toString())
                .postCode(profile.map(p -> p.getAddress().postCode()).toString())
                .cardId(cardId.isEmpty() ? "" : cardId)
                .build();
    }

    private Mono<Profile> createStaff(UserRequestDto userRequestDto,
                                      Set<SimpleGrantedAuthority> grantedAuthorities) {
        Profile authProfile = builtProfile(userRequestDto, grantedAuthorities);

        Mono<Profile> profile = profileRepository.insert(authProfile);
        log.info("The profile was successfully create");
        return profile;
    }

    private UserDto createUser(UserRequestDto userRequestDto,
                               Set<SimpleGrantedAuthority> grantedAuthorities) {
        Profile authProfile = builtProfile(userRequestDto, grantedAuthorities);

        PaymentDto paymentCustomerId = createPaymentCustomer(userRequestDto.username());
        log.info("Payment customer was successfully create: {}", paymentCustomerId);

        PaymentDto paymentDto = addCardToCustomer(userRequestDto, paymentCustomerId.customerId());
        log.info("Payment card id {} was successfully added to payment customer", paymentDto.cardId());

        UserDto profile = profileRepository.insert(authProfile)
                .map(p -> UserDto.builder()
                        .id(p.getId())
                        .username(p.getUsername())
                        .password("")
                        .grantedAuthorities(p.getGrantedAuthorities())
                        .firstName(p.getFirstName())
                        .lastName(p.getLastName())
                        .phoneNumber(p.getPhoneNumber())
                        .city(p.getAddress().city())
                        .street(p.getAddress().street())
                        .postCode(p.getAddress().postCode())
                        .cardId(paymentDto.cardId())
                        .build())
                .doOnSuccess(System.out::println)
                .block();
        log.info("The profile was successfully create");
        return profile;
    }

    private Profile builtProfile(UserRequestDto model, Set<SimpleGrantedAuthority> grantedAuthorities) {
        Mono<Profile> username = profileRepository.findByUsername(model.username());
        if (username.blockOptional().isPresent()) {
            throw new InvalidRequestDataException(String.format("Profile already exist: %s", model.username()));
        }
        Address address = new Address(
                model.city(),
                model.street(),
                model.postCode());

        return Profile.builder()
                .username(model.username())
                .password(passwordEncoder.encode(model.password().trim()))
                .grantedAuthorities(grantedAuthorities)
                .firstName(model.firstName())
                .lastName(model.lastName())
                .address(address)
                .phoneNumber(model.phoneNumber())
                .created(OffsetDateTime.now())
                .build();
    }

    private PaymentDto addCardToCustomer(UserRequestDto userRequestDto,
                                         String customerId) {
        String cardRequestBody = mapper.toJson(CardDto.builder()
                .customerId(customerId)
                .cardNumber(userRequestDto.cardNumber())
                .cardExpMonth(userRequestDto.cardExpMonth())
                .cardExpYear(userRequestDto.cardExpYear())
                .cardCvc(userRequestDto.cardCvc())
                .build()
        );

        return sendRequestToPaymentService(
                paymentServiceCreateCardUri,
                cardRequestBody
        );
    }

    private PaymentDto createPaymentCustomer(String username) {
        String customerRequestBody = mapper.toJson(PaymentDto.builder()
                .username(username)
                .customerName(username)
                .build()
        );

        return sendRequestToPaymentService(
                paymentServiceCreateNewCustomerUri,
                customerRequestBody
        );
    }

    private PaymentDto sendRequestToPaymentService(String uri,
                                                   String request) {
        PaymentDto paymentDto = webClient
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PaymentDto.class)
                .transform(it ->
                        reactiveCircuitBreakerFactory.create("profileService")
                                .run(it, throwable -> {
                                    log.warn("Payment service is down", throwable);
                                    return Mono.just(PaymentDto.builder().customerId("").build());
                                })
                )
                .block();

        checkAvailabilityOfPaymentService(paymentDto.customerId());

        return paymentDto;
    }

    private void deletePaymentCustomer(String username) {
        String paymentCustomerId = webClient
                .delete()
                .uri(paymentServiceDeleteCustomerByUsernameUri + username)
                .retrieve()
                .bodyToMono(String.class)
                .transform(it ->
                        reactiveCircuitBreakerFactory.create("profileService")
                                .run(it, throwable -> {
                                    log.warn("Payment service is down", throwable);
                                    return Mono.just("");
                                })
                )
                .block();

        checkAvailabilityOfPaymentService(Objects.requireNonNull(paymentCustomerId));
    }

    private void checkAvailabilityOfPaymentService(String token) {
        if (token.isEmpty()) {
            throw new InvalidRequestDataException("""
                    Something happened with the profile service.
                    Please check the request details again
                    """);
        }
    }
}
