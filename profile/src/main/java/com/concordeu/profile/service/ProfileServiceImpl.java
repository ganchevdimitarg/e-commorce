package com.concordeu.profile.service;

import com.concordeu.client.common.dto.ReplayPaymentDto;
import com.concordeu.profile.config.KafkaProducerConfig;
import com.concordeu.profile.dto.CardDto;
import com.concordeu.profile.dto.PaymentDto;
import com.concordeu.profile.dto.UserDto;
import com.concordeu.profile.dto.UserRequestDto;
import com.concordeu.profile.entities.Address;
import com.concordeu.profile.entities.Profile;
import com.concordeu.profile.excaption.InvalidRequestDataException;
import com.concordeu.profile.repositories.ProfileRepository;
import com.concordeu.profile.validation.ValidateData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.protocol.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.http.MediaType;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.kafka.requestreply.RequestReplyFuture;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static com.concordeu.profile.security.UserRole.*;


@Service
@Slf4j
public class ProfileServiceImpl implements ProfileService {
    private final PasswordEncoder passwordEncoder;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory;
    private final ProfileRepository profileRepository;
    private final JwtService jwtService;
    private final ValidateData validateData;
    private final ReplyingKafkaTemplate<String, ReplayPaymentDto, String> template;

    @Value("${payment.service.customer.post.uri}")
    private String paymentServiceCreateNewCustomerUri;
    @Value("${payment.service.customer.delete.uri}")
    private String paymentServiceDeleteCustomerByUsernameUri;
    @Value("${payment.service.card.post.uri}")
    private String paymentServiceCreateCardUri;
    @Value("${payment.service.card.get.uri}")
    private String paymentServiceGetCardsByUsernameUri;

    public ProfileServiceImpl(PasswordEncoder passwordEncoder,
                              WebClient.Builder webClientBuilder,
                              ObjectMapper objectMapper,
                              ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory,
                              ProfileRepository profileRepository,
                              JwtService jwtService,
                              ValidateData validateData,
                              ReplyingKafkaTemplate<String, ReplayPaymentDto, String> template) {
        this.passwordEncoder = passwordEncoder;
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.reactiveCircuitBreakerFactory = reactiveCircuitBreakerFactory;
        this.profileRepository = profileRepository;
        this.jwtService = jwtService;
        this.validateData = validateData;
        this.template = template;
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

        Mono<Profile> profileMono = profileRepository.findByUsername(username);
        Profile[] profile = new Profile[1];
        profileMono.subscribe(p -> profile[0] = p);

        deletePaymentCustomer(profile[0].getUsername());

        profileRepository.delete(profile[0]).subscribe();
        log.info("User with username: {} was successfully deleted", username);
    }

    @Override
    public Mono<UserDto> getUserByUsername(String username) {
        Assert.hasLength(username, "Username is empty");

        template.setReplyErrorChecker(record -> {
            Header error = record.headers().lastHeader("serverSentAnError");
            if (error != null) {
                return new IllegalArgumentException(new String(error.value()));
            } else {
                return null;
            }
        });

        ConsumerRecord<String, String> consumerRecord = null;
        ReplayPaymentDto paymentDto = null;
        RequestReplyFuture<String, ReplayPaymentDto, String> future = template.sendAndReceive(
                new ProducerRecord<>(KafkaProducerConfig.GET_CARDS_BY_USERNAME,
                        ReplayPaymentDto.builder().username(username).build()));
        try {
            future.getSendFuture().get(5, TimeUnit.SECONDS);
            consumerRecord = future.get(5, TimeUnit.SECONDS);

            paymentDto = objectMapper.readValue(consumerRecord.value(), ReplayPaymentDto.class);
        } catch (InterruptedException | TimeoutException | ExecutionException | JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        ReplayPaymentDto finalPaymentDto = paymentDto;
        Mono<Profile> byUsername = profileRepository.findByUsername(username);
        Mono<UserDto> map = byUsername
                .map(p ->
                        UserDto.builder()
                                .id(p.getId())
                                .username(p.getUsername())
                                .firstName(p.getFirstName() == null ? "N/A" : p.getFirstName())
                                .lastName(p.getLastName() == null ? "N/A" : p.getLastName())
                                .phoneNumber(p.getPhoneNumber() == null ? "N/A" : p.getPhoneNumber())
                                .city(p.getAddress() == null ? "N/A" : p.getAddress().city())
                                .street(p.getAddress() == null ? "N/A" : p.getAddress().street())
                                .postCode(p.getAddress() == null ? "N/A" : p.getAddress().postCode())
                                .cardId(finalPaymentDto.cards().isEmpty() ? StringUtil.EMPTY_STRING : finalPaymentDto.cards().stream().findFirst().get())
                                .build()
                );
        return map;
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
//        producer.sendPasswordResetTokenMail(username, token);
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
                .firstName(profile.map(Profile::getFirstName).toString())
                .lastName(profile.map(Profile::getLastName).toString())
                .phoneNumber(profile.map(Profile::getPhoneNumber).toString())
                .city(profile.map(p -> p.getAddress().city()).toString())
                .street(profile.map(p -> p.getAddress().street()).toString())
                .postCode(profile.map(p -> p.getAddress().postCode()).toString())
                .cardId(cardId)
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

        String paymentCustomerId = createPaymentCustomer(userRequestDto.username());
        log.info("Payment customer was successfully create: {}", paymentCustomerId);

        String paymentDto = addCardToCustomer(userRequestDto, paymentCustomerId);
        log.info("Payment card id {} was successfully added to payment customer", paymentDto);
        AtomicReference<UserDto> u = null;
        profileRepository.insert(authProfile)
                .subscribe(p -> u.set(UserDto.builder()
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
                        .cardId(paymentDto)
                        .build())
                );
        log.info("The profile was successfully create");
        return u.get();
    }

    private Profile builtProfile(UserRequestDto model, Set<SimpleGrantedAuthority> grantedAuthorities) {
        profileRepository.findByUsername(model.username())
                .subscribe(
                        throwable -> {
                            throw new InvalidRequestDataException(String.format("Profile already exist: %s", model.username()));
                        }
                );

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

    private String addCardToCustomer(UserRequestDto userRequestDto,
                                     String customerId) {
        String cardRequestBody = null;
        try {
            cardRequestBody = objectMapper.writeValueAsString(
                    CardDto.builder()
                            .customerId(customerId)
                            .cardNumber(userRequestDto.cardNumber())
                            .cardExpMonth(userRequestDto.cardExpMonth())
                            .cardExpYear(userRequestDto.cardExpYear())
                            .cardCvc(userRequestDto.cardCvc())
                            .build()
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return sendRequestToPaymentService(
                paymentServiceCreateCardUri,
                cardRequestBody
        );
    }

    private String createPaymentCustomer(String username) {
        String customerRequestBody = null;
        try {
            customerRequestBody = objectMapper.writeValueAsString(
                    PaymentDto.builder()
                            .username(username)
                            .customerName(username)
                            .build()
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return sendRequestToPaymentService(
                paymentServiceCreateNewCustomerUri,
                customerRequestBody
        );
    }

    private String sendRequestToPaymentService(String uri,
                                               String request) {

        return webClient
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(String.class)
                .transform(it ->
                        reactiveCircuitBreakerFactory.create("profileService")
                                .run(it, throwable -> {
                                    log.warn("Payment service is down", throwable);
                                    return Mono.just("");
                                })
                )
                .blockOptional()
                .get();
    }

    private void deletePaymentCustomer(String username) {
        webClient
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
                .subscribe(throwable -> throwInvalidRequestDataException());
    }

    private static void throwInvalidRequestDataException() {
        throw new InvalidRequestDataException("""
                Something happened with the profile service.
                Please check the request details again
                """);
    }
}
