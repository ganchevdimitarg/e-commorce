package com.concordeu.profile.service;

import com.concordeu.client.common.constant.Constant;
import com.concordeu.client.common.dto.CardDto;
import com.concordeu.client.common.dto.ReplayPaymentDto;
import com.concordeu.client.common.ProfileGrantedAuthority;
import com.concordeu.profile.dto.UserDto;
import com.concordeu.client.common.dto.UserRequestDto;
import com.concordeu.profile.entities.Address;
import com.concordeu.profile.entities.Profile;
import com.concordeu.profile.excaption.InvalidRequestDataException;
import com.concordeu.profile.repositories.ProfileRepository;
import com.concordeu.profile.validation.ValidateData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.concordeu.profile.security.UserRole.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileServiceImpl implements ProfileService {
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final ProfileRepository profileRepository;
    private final JwtService jwtService;
    private final ValidateData validateData;
    private final MailService mailService;
    private final ReplyingKafkaTemplate<String, ReplayPaymentDto, String> template;

    @Override
    public Mono<UserDto> createAdmin(UserRequestDto userRequestDto) {
        Mono<UserDto> userDto = createStaff(userRequestDto, ADMIN.getGrantedAuthorities());
        log.debug("Admin user with username: {} was created", userDto.toFuture().getNow(UserDto.builder().build()).username());
        return userDto;
    }

    @Override
    public Mono<UserDto> createWorker(UserRequestDto userRequestDto) {
        Mono<UserDto> userDto = createStaff(userRequestDto, WORKER.getGrantedAuthorities());
        log.debug("Worker user with username: {} was created", userDto.toFuture().getNow(UserDto.builder().build()).username());
        return userDto;
    }

    @Override
    public Mono<UserDto> createUser(UserRequestDto userRequestDto) throws ExecutionException, InterruptedException, JsonProcessingException, TimeoutException {
        UserDto userDto = createUser(userRequestDto, USER.getGrantedAuthorities());
        log.debug("Profile user with username: {} was created", userDto.username());
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
        log.debug("Profile with username {} is update", profile.getUsername());
    }

    @Override
    public void deleteUser(String username) throws ExecutionException, InterruptedException, JsonProcessingException, TimeoutException {
        Assert.hasLength(username, "Username is empty");

        Profile profile = profileRepository.findByUsername(username)
                .toFuture()
                .getNow(Profile.builder().build());

        getReplayPaymentDto(Constant.DELETE_BY_USERNAME,
                ReplayPaymentDto.builder()
                        .username(profile.getUsername())
                        .build());

        profileRepository.delete(profile).subscribe();
        log.debug("User with username: {} was successfully deleted", username);
    }

    @Override
    public Mono<UserDto> getUserByUsername(String username) throws ExecutionException, InterruptedException, JsonProcessingException, TimeoutException {
        Assert.hasLength(username, "Username is empty");
        Set<CardDto> cardDto = getReplayPaymentDto(
                Constant.GET_CARDS_BY_USERNAME,
                ReplayPaymentDto.builder().username(username).build()
        )
                .cards();

        return profileRepository.findByUsername(username)
                .map(p -> UserDto.builder()
                        .id(p.getId())
                        .username(p.getUsername())
                        .firstName(p.getFirstName())
                        .lastName(p.getLastName())
                        .phoneNumber(p.getPhoneNumber())
                        .city(p.getAddress().city())
                        .street(p.getAddress().street())
                        .postCode(p.getAddress().postCode())
                        .cardId(cardDto == null || cardDto.isEmpty() ? "N/A" : cardDto.stream().findFirst().get().cardId())
                        .cardNumber(cardDto == null || cardDto.isEmpty() ? "N/A" : cardDto.stream().findFirst().get().cardNumber())
                        .cardExpMonth(cardDto == null || cardDto.isEmpty() ? 0 : cardDto.stream().findFirst().get().cardExpMonth())
                        .cardExpYear(cardDto == null || cardDto.isEmpty() ? 0 : cardDto.stream().findFirst().get().cardExpYear())
                        .cardCvc(cardDto == null || cardDto.isEmpty() ? "N/A" : cardDto.stream().findFirst().get().cardCvc())
                        .build()
                );
    }

    @Override
    public Mono<String> passwordReset(String username) {
        profileRepository.findByUsername(username)
                .subscribe(
                        throwable -> {
                            throw new InvalidRequestDataException(String.format("User does not exist: %s", username));
                        });
        String token = jwtService.generateToken(
                new User(
                        username,
                        "",
                        USER.getGrantedAuthorities()
                )
        );

        mailService.sendPasswordResetTokenMail(username, token);
        log.debug("Successfully generated password reset token");

        return Mono.just(token);
    }

    @Override
    public boolean isPasswordResetTokenValid(String token) {
        return jwtService.isTokenValid(token);
    }

    @Override
    public void setNewPassword(String username,
                               String password) {
        Profile profile = profileRepository.findByUsername(username).toFuture().getNow(Profile.builder().build());

        if (profile != null) {
            validateData.isValidPassword(password);
            Objects.requireNonNull(profile).setPassword(passwordEncoder.encode(password));
            profileRepository.save(profile).subscribe();
            log.debug("The password of user {} has been changed successfully", username);
        } else {
            log.warn(String.format("User does not exist: %s", username));
            throw new InvalidRequestDataException(String.format("User does not exist: %s", username));
        }
    }

    private Mono<UserDto> createStaff(UserRequestDto userRequestDto,
                                      Set<ProfileGrantedAuthority> grantedAuthorities) {
        Profile authProfile = builtProfile(userRequestDto, grantedAuthorities);

        Mono<Profile> profile = profileRepository.save(authProfile);
        log.debug("The profile was successfully create");

        return profile.map(p -> UserDto.builder()
                .id(p.getId())
                .username(p.getUsername())
                .firstName(p.getFirstName())
                .lastName(p.getLastName())
                .phoneNumber(p.getPhoneNumber())
                .city(p.getAddress().city())
                .street(p.getAddress().street())
                .postCode(p.getAddress().postCode())
                .build());
    }

    private UserDto createUser(UserRequestDto userRequestDto,
                               Set<ProfileGrantedAuthority> grantedAuthorities) throws ExecutionException, InterruptedException, JsonProcessingException, TimeoutException {

        Profile authProfile = builtProfile(userRequestDto, grantedAuthorities);

        String customerId = getReplayPaymentDto(
                Constant.CREATE_CUSTOMER,
                ReplayPaymentDto.builder()
                        .userRequestDto(userRequestDto)
                        .build()
        )
                .paymentDto()
                .customerId();
        log.debug("Payment customer was successfully create: {}", customerId);

        String cardId = "";
        if (!userRequestDto.cardNumber().isBlank()) {
            cardId = getReplayPaymentDto(
                    Constant.ADD_CARD_TO_CUSTOMER,
                    ReplayPaymentDto.builder()
                            .cardDto(CardDto.builder()
                                    .customerId(customerId)
                                    .cardNumber(userRequestDto.cardNumber())
                                    .cardExpMonth(userRequestDto.cardExpMonth())
                                    .cardExpYear(userRequestDto.cardExpYear())
                                    .cardCvc(userRequestDto.cardCvc())
                                    .build())
                            .build()
            )
                    .cardDto()
                    .cardId();
        }

        log.debug("Payment card id {} was successfully added to payment customer", cardId.isBlank() ? "" : cardId);

        log.debug("The profile was successfully create");

        Profile profile;
        try {
            profile = profileRepository.save(authProfile).toFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        return UserDto.builder()
                .id(profile.getId())
                .username(profile.getUsername())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .phoneNumber(profile.getPhoneNumber())
                .city(profile.getAddress().city())
                .street(profile.getAddress().street())
                .postCode(profile.getAddress().postCode())
                .cardNumber(userRequestDto.cardNumber())
                .cardExpMonth(userRequestDto.cardExpMonth())
                .cardExpYear(userRequestDto.cardExpYear())
                .cardCvc(userRequestDto.cardCvc())
                .build();

    }

    private Profile builtProfile(UserRequestDto model, Set<ProfileGrantedAuthority> grantedAuthorities) {
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
                .build();
    }

    private ReplayPaymentDto getReplayPaymentDto(String topic, ReplayPaymentDto payload) throws ExecutionException, InterruptedException, TimeoutException, JsonProcessingException {
        template.setReplyErrorChecker(record -> {
            Header error = record.headers().lastHeader(Constant.SERVER_SENT_AN_ERROR);
            if (error != null) {
                return new IllegalArgumentException(new String(error.value()));
            } else {
                return null;
            }
        });

        ReplayPaymentDto paymentDto;

            paymentDto = objectMapper.readValue(
                    template.sendAndReceive(new ProducerRecord<>(topic, payload))
                            .get(5, TimeUnit.SECONDS)
                            .value(),
                    ReplayPaymentDto.class
            );

        return paymentDto;
    }
}
