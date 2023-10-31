package com.concordeu.profile.service.impl;

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
import com.concordeu.profile.service.JwtService;
import com.concordeu.profile.service.MailService;
import com.concordeu.profile.service.ProfileService;
import com.concordeu.profile.validation.ValidateData;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.annotation.Observed;
import jdk.jshell.spi.ExecutionControl;
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

    public UserDto createWorker(UserRequestDto userRequestDto) {
        UserDto userDto = createStaff(userRequestDto, WORKER.getGrantedAuthorities());
        log.debug("Worker user with username: {} was created", userDto.username());
        return userDto;
    }

    @Override

    public UserDto createUser(UserRequestDto userRequestDto) throws ExecutionException, InterruptedException, JsonProcessingException, TimeoutException {
        UserDto userDto = createUser(userRequestDto, USER.getGrantedAuthorities());
        log.debug("Profile user with username: {} was created", userDto.username());
        return userDto;
    }

    @Override
    public void updateUser(String username,
                           UserRequestDto userRequestDto) {
        Assert.hasLength(username, "Username is empty");

        Profile profile = getProfile(username);

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

        profileRepository.save(profile);
        log.debug("Profile with username {} is update", profile.getUsername());
    }

    @Override
    public void deleteUser(String username) throws ExecutionException, InterruptedException, JsonProcessingException, TimeoutException {
        Assert.hasLength(username, "Username is empty");

        Profile profile = getProfile(username);

        getReplayPaymentDto(Constant.DELETE_BY_USERNAME,
                ReplayPaymentDto.builder()
                        .username(profile.getUsername())
                        .build());

        profileRepository.delete(profile);
        log.debug("User with username: {} was successfully deleted", username);
    }

    @Override
    public UserDto getUserByUsername(String username) throws ExecutionException, InterruptedException, JsonProcessingException, TimeoutException {
        Assert.hasLength(username, "Username is empty");
        Set<CardDto> cardDto = getReplayPaymentDto(
                Constant.GET_CARDS_BY_USERNAME,
                ReplayPaymentDto.builder().username(username).build()
        )
                .cards();

        Profile profile = getProfile(username);

        return UserDto.builder()
                .id(profile.getId())
                .username(profile.getUsername())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .phoneNumber(profile.getPhoneNumber())
                .city(profile.getAddress().city())
                .street(profile.getAddress().street())
                .postCode(profile.getAddress().postCode())
                .cardId(cardDto == null || cardDto.isEmpty() ? "N/A" : cardDto.stream().findFirst().get().cardId())
                .cardNumber(cardDto == null || cardDto.isEmpty() ? "N/A" : cardDto.stream().findFirst().get().cardNumber())
                .cardExpMonth(cardDto == null || cardDto.isEmpty() ? 0 : cardDto.stream().findFirst().get().cardExpMonth())
                .cardExpYear(cardDto == null || cardDto.isEmpty() ? 0 : cardDto.stream().findFirst().get().cardExpYear())
                .cardCvc(cardDto == null || cardDto.isEmpty() ? "N/A" : cardDto.stream().findFirst().get().cardCvc())
                .build();
    }

    @Override
    public String passwordReset(String username) {
        getProfile(username);

        String token = jwtService.generateToken(
                new User(
                        username,
                        "",
                        USER.getGrantedAuthorities()
                )
        );

        mailService.sendPasswordResetTokenMail(username, token);
        log.debug("Successfully generated password reset token");

        return token;
    }

    @Override
    public boolean isPasswordResetTokenValid(String token) {
        return jwtService.isTokenValid(token);
    }

    @Override
    public void setNewPassword(String username,
                               String password) {
        Profile profile = getProfile(username);

        if (profile != null) {
            validateData.isValidPassword(password);
            Objects.requireNonNull(profile).setPassword(passwordEncoder.encode(password));

            profileRepository.save(profile);
            log.debug("The password of user {} has been changed successfully", username);
        } else {
            throw new InvalidRequestDataException(String.format("User does not exist: %s", username));
        }
    }

    private UserDto createStaff(UserRequestDto userRequestDto,
                                Set<ProfileGrantedAuthority> grantedAuthorities) {
        Profile authProfile = builtProfile(userRequestDto, grantedAuthorities);

        Profile profile = profileRepository.save(authProfile);
        log.debug("The profile was successfully create");

        return UserDto.builder()
                .id(profile.getId())
                .username(profile.getUsername())
                .firstName(profile.getFirstName())
                .lastName(profile.getLastName())
                .phoneNumber(profile.getPhoneNumber())
                .city(profile.getAddress().city())
                .street(profile.getAddress().street())
                .postCode(profile.getAddress().postCode())
                .build();
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

        Profile profile = profileRepository.save(authProfile);


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
                .cardId(cardId)
                .build();

    }

    private Profile builtProfile(UserRequestDto model, Set<ProfileGrantedAuthority> grantedAuthorities) {
        if (profileRepository.findByUsername(model.username()).isPresent()) {
            throw new IllegalArgumentException(
                    String.format("The username already exists: %s", model.username()));
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
                .build();
    }


    private Profile getProfile(String username) {
        return profileRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Profile does not exist"));
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
