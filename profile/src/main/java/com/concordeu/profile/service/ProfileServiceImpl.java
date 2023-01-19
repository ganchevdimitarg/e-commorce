package com.concordeu.profile.service;

import com.concordeu.profile.dao.ProfileDao;
import com.concordeu.profile.domain.Address;
import com.concordeu.profile.domain.Profile;
import com.concordeu.profile.dto.CardDto;
import com.concordeu.profile.dto.PaymentDto;
import com.concordeu.profile.dto.UserDto;
import com.concordeu.profile.dto.UserRequestDto;
import com.concordeu.profile.excaption.InvalidRequestDataException;
import com.concordeu.profile.validation.ValidateData;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.Set;

import static com.concordeu.client.security.UserRole.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileServiceImpl implements ProfileService {
    private final PasswordEncoder passwordEncoder;
    private final WebClient webClient;
    private final Gson mapper;
    private final ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory;
    private final ProfileDao profileDao;
    private final JwtService jwtService;
    private final MailService mailService;
    private final ValidateData validateData;
    @Value("${payment.service.customer.post.uri}")
    private String paymentCustomerPostUri;
    @Value("${payment.service.card.post.uri}")
    private String paymentCardPostUri;


    @Override
    public UserDto createAdmin(UserRequestDto userRequestDto) {
        UserDto userDto = getUserDto(createStaff(userRequestDto, ADMIN.getGrantedAuthorities()));
        log.info("Admin user with username: {} was created", userDto.username());
        return userDto;
    }

    @Override
    public UserDto createWorker(UserRequestDto userRequestDto) {
        UserDto userDto = getUserDto(createStaff(userRequestDto, WORKER.getGrantedAuthorities()));
        log.info("Worker user with username: {} was created", userDto.username());
        return userDto;
    }

    @Override
    public UserDto createUser(UserRequestDto userRequestDto) {
        UserDto userDto = getUserDto(createUser(userRequestDto, USER.getGrantedAuthorities()));
        log.info("Profile user with username: {} was created", userDto.username());
        return userDto;
    }

    @Override
    public void updateUser(String username,
                           UserRequestDto userRequestDto) {
        Assert.hasLength(username, "Username is empty");

        Profile profile = profileDao.findByUsername(username)
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

        profileDao.save(profile);
        log.info("Profile with username {} is update", profile.getUsername());
    }

    @Override
    public void deleteUser(String username) {
        Assert.hasLength(username, "Username is empty");
        Profile profile = profileDao.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Profile does not exist");
                    return new UsernameNotFoundException("Profile does not exist");
                });

        profileDao.delete(profile);
        log.info("ser with username: {} was successfully deleted", username);
    }

    @Override
    public UserDto getUserByUsername(String username) {
        Assert.hasLength(username, "Username is empty");
        Profile profile = profileDao.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Profile does not exist"));
        profile.setPassword("");
        return getUserDto(profile);
    }

    @Override
    public String passwordReset(String username) {
        profileDao.findByUsername(username)
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
        return token;
    }

    @Override
    public boolean isPasswordResetTokenValid(String token) {
        return jwtService.isTokenValid(token);
    }

    @Override
    public void setNewPassword(String username,
                               String password) {
        Profile profile = profileDao.findByUsername(username)
                .orElseThrow(() -> new InvalidRequestDataException("User does not exist"));
        validateData.isValidPassword(password);
        profile.setPassword(passwordEncoder.encode(password));
        profileDao.save(profile);
        log.info("The password of user {} has been changed successfully", username);
    }

    private UserDto getUserDto(Profile profile) {
        return new UserDto(
                profile.getId(),
                profile.getUsername(),
                profile.getPassword(),
                profile.getGrantedAuthorities(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getPhoneNumber(),
                profile.getAddress().city(),
                profile.getAddress().street(),
                profile.getAddress().postCode());
    }

    private Profile createStaff(UserRequestDto userRequestDto,
                                Set<SimpleGrantedAuthority> grantedAuthorities) {
        Profile authProfile = builtProfile(userRequestDto, grantedAuthorities);

        Profile profile = profileDao.insert(authProfile);
        log.info("The profile was successfully create");
        return profile;
    }

    private Profile createUser(UserRequestDto userRequestDto,
                               Set<SimpleGrantedAuthority> grantedAuthorities) {
        Profile authProfile = builtProfile(userRequestDto, grantedAuthorities);


        String paymentCustomerId = createPaymentCustomer(userRequestDto.username()).customerId();

        addCardToCustomer(userRequestDto, paymentCustomerId);

        Profile profile = profileDao.insert(authProfile);
        log.info("The profile was successfully create");
        return profile;
    }

    private Profile builtProfile(UserRequestDto model, Set<SimpleGrantedAuthority> grantedAuthorities) {
        if (profileDao.findByUsername(model.username()).isPresent()) {
            throw new InvalidRequestDataException(String.format("Profile already exist: %s", model.username()));
        }
        Address address = new Address(
                model.city(),
                model.street(),
                model.postCode());

        Profile authProfile = Profile.builder()
                .username(model.username())
                .password(passwordEncoder.encode(model.password().trim()))
                .grantedAuthorities(grantedAuthorities)
                .firstName(model.firstName())
                .lastName(model.lastName())
                .address(address)
                .phoneNumber(model.phoneNumber())
                .created(LocalDateTime.now())
                .build();
        return authProfile;
    }

    private void addCardToCustomer(UserRequestDto userRequestDto,
                                   String customerId) {
        String cardRequestBody = mapper.toJson(CardDto.builder()
                .customerId(customerId)
                .cardNumber(userRequestDto.cardNumber())
                .cardExpMonth(userRequestDto.cardExpMonth())
                .cardExpYear(userRequestDto.cardExpYear())
                .cardCvc(userRequestDto.cardCvc())
                .build()
        );

        sendRequestToPaymentService(
                paymentCardPostUri,
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
                paymentCustomerPostUri,
                customerRequestBody
        );
    }

    private PaymentDto sendRequestToPaymentService(String uri,
                                                   String request) {
        return webClient
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PaymentDto.class)
                /*.transform(it ->
                        reactiveCircuitBreakerFactory.create("profile-service")
                                .run(it, throwable -> (Mono.just(PaymentDto.builder().username("Ooops...").build())))
                )*/
                .block();
    }
}
