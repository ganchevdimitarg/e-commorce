package com.concordeu.profile.service;

import com.concordeu.profile.dao.UserDao;
import com.concordeu.profile.domain.Address;
import com.concordeu.profile.domain.User;
import com.concordeu.profile.dto.NotificationDto;
import com.concordeu.profile.dto.UserDto;
import com.concordeu.profile.dto.UserRequestDto;
import com.concordeu.profile.excaption.InvalidRequestDataException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.concordeu.profile.security.UserRole.USER;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileServiceImpl implements ProfileService {

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;
    private final WebClient webClient;

    @Override
    public UserDto createUser(UserRequestDto model) {
        if (userDao.findByUsername(model.username()).isPresent()) {
            throw new InvalidRequestDataException("User already exist: " + model.username());
        }
        Address address = new Address(
                model.city(),
                model.street(),
                model.postCode());

        User authUser = User.builder()
                .username(model.username())
                .password(passwordEncoder.encode(model.password().trim().isEmpty() ? "" : model.password().trim()))
                .grantedAuthorities(USER.getGrantedAuthorities())
                .firstName(model.firstName())
                .lastName(model.lastName())
                .address(address)
                .phoneNumber(model.phoneNumber())
                .created(LocalDateTime.now())
                .build();

        User user = userDao.insert(authUser);
        log.info("The user was successfully create");

        String notificationBody = new Gson().toJson(
                new NotificationDto(
                        user.getUsername(),
                        "Registration",
                        "You have successfully registered. Please log in to your account.")
        );

        String value = webClient
                .post()
                .uri("/api/v1/notification/sendMail")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(notificationBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return getUserDto(user);
    }

    @Override
    public UserDto getOrCreateUser(String username) {
        Assert.hasLength(username, "Username is empty!");
        Optional<User> user = userDao.findByUsername(username);

        return getUserDto(user.orElseGet(() -> createUserWithEmail(username)));
    }

    @Override
    public void updateUser(String username, UserRequestDto requestDto) {
        Assert.hasLength(username, "Username is empty");
        User user = userDao.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User does not exist"));
        Address address = new Address(
                requestDto.city(),
                requestDto.street(),
                requestDto.postCode());

        user.setUsername(requestDto.username());
        user.setPassword(requestDto.password());
        user.setFirstName(requestDto.firstName());
        user.setLastName(requestDto.lastName());
        user.setPhoneNumber(requestDto.phoneNumber());
        user.setAddress(address);

        userDao.save(user);
        log.info("User with username {} is update", user.getUsername());
    }

    @Override
    public void deleteUser(String username) {
        Assert.hasLength(username, "Username is empty");
        User user = userDao.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User does not exist"));

        userDao.delete(user);
    }

    @Override
    public UserDto getUserByUsername(String username) {
        Assert.hasLength(username, "Username is empty");
        User user = userDao.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User does not exist"));
        user.setPassword("");
        return getUserDto(user);
    }

    private User createUserWithEmail(String username) {
        log.info("Create user with username");
        User user = User.builder()
                .username(username)
                .password("")
                .grantedAuthorities(USER.getGrantedAuthorities())
                .created(LocalDateTime.now())
                .build();
        return userDao.insert(user);
    }

    private UserDto getUserDto(User user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getGrantedAuthorities(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber(),
                user.getAddress().city(),
                user.getAddress().street(),
                user.getAddress().postCode());
    }
}
