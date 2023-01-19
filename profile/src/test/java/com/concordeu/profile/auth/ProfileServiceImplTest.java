package com.concordeu.profile.auth;

import com.concordeu.profile.dao.ProfileDao;
import com.concordeu.profile.domain.Address;
import com.concordeu.profile.domain.Profile;
import com.concordeu.profile.dto.UserDto;
import com.concordeu.profile.dto.UserRequestDto;
import com.concordeu.profile.excaption.InvalidRequestDataException;
import com.concordeu.profile.service.JwtService;
import com.concordeu.profile.service.MailService;
import com.concordeu.profile.service.ProfileService;
import com.concordeu.profile.service.ProfileServiceImpl;
import com.concordeu.profile.validation.ValidateData;
import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashSet;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {
    ProfileService testService;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    WebClient webClient;
    @Mock
    Gson mapper;
    @Mock
    ReactiveCircuitBreakerFactory reactiveCircuitBreakerFactory;
    @Mock
    ProfileDao profileDao;
    @Mock
    JwtService jwtService;
    @Mock
    MailService mailService;
    @Mock
    ValidateData validateData;

    UserRequestDto model;
    Profile profile;

    @BeforeEach
    void setUp() {
        testService = new ProfileServiceImpl(
                passwordEncoder,
                webClient,
                mapper,
                reactiveCircuitBreakerFactory,
                profileDao,
                jwtService,
                mailService,
                validateData);

        model = new UserRequestDto(
                "dimitarggacnhev3@gmail.com",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "0888888888",
                "Varna",
                "Katay",
                "9000",
                "4242424242424242",
                8,
                2030,
                "333");
        profile = Profile.builder()
                .id("1")
                .username("example@gmail.com")
                .username("abc!@#ABC")
                .grantedAuthorities(new HashSet<>())
                .firstName("ivan").lastName("ivanov")
                .address(new Address("varna", "katya", "9000"))
                .build();

    }

    @Test
    @Disabled
    void createUserShouldCreateUserIfUserNotExist() {
        when(profileDao.findByUsername(model.username())).thenReturn(Optional.empty());
        testService.createUser(model);

        ArgumentCaptor<Profile> argumentCaptor = ArgumentCaptor.forClass(Profile.class);
        verify(profileDao).insert(argumentCaptor.capture());

        Profile captorProfile = argumentCaptor.getValue();
        assertThat(captorProfile).isNotNull();
        assertThat(captorProfile.getUsername()).isEqualTo(model.username());
    }

    @Test
    void createUserShouldThrowExceptionIfUserExist() {
        when(profileDao.findByUsername(model.username())).thenReturn(Optional.of(Profile.builder().build()));

        assertThatThrownBy(() -> testService.createUser(model))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining("Profile already exist: " + model.username());

        verify(profileDao, never()).insert(any(Profile.class));
    }

    @Test
    void updateUserShouldUpdateUserInfo() {
        Profile profileBefore = Profile.builder().firstName("Dimitar").build();
        when(profileDao.findByUsername(model.username())).thenReturn(Optional.of(profileBefore));

        testService.updateUser(model.username(), model);

        ArgumentCaptor<Profile> argumentCaptor = ArgumentCaptor.forClass(Profile.class);
        verify(profileDao).save(argumentCaptor.capture());

        Profile profileAfter = argumentCaptor.getValue();
        assertThat(profileAfter).isNotNull();
        assertThat(profileAfter).isEqualTo(profileBefore);
        assertThat(profileAfter.getUsername()).isEqualTo(model.username());
    }

    @Test
    void updateUserShouldThrowExceptionIfEmailIsEmpty() {
        assertThatThrownBy(() -> testService.updateUser("", model))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username is empty");

        verify(profileDao, never()).save(any(Profile.class));
    }

    @Test
    void updateUserShouldThrowExceptionIfUserDoesNotExist() {
        when(profileDao.findByUsername("ivan@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testService.updateUser("ivan@gmail.com", model))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("Profile does not exist");

        verify(profileDao, never()).save(any(Profile.class));
    }

    @Test
    void deleteUserShouldDeleteUser() {
        when(profileDao.findByUsername(model.username())).thenReturn(Optional.of(Profile.builder().build()));

        testService.deleteUser(model.username());

        verify(profileDao).delete(any(Profile.class));
    }

    @Test
    void deleteUserShouldThrowExceptionIfEmailIsEmpty() {
        assertThatThrownBy(() -> testService.deleteUser(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username is empty");

        verify(profileDao, never()).save(any(Profile.class));
    }

    @Test
    void deleteUserShouldThrowExceptionIfUserDoesNotExist() {
        when(profileDao.findByUsername("ivan@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testService.deleteUser("ivan@gmail.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("Profile does not exist");

        verify(profileDao, never()).delete(any(Profile.class));
    }

    @Test
    void getUserByUsernameShouldReturnUser() {

        when(profileDao.findByUsername(model.username())).thenReturn(Optional.of(profile));

        UserDto testUser = testService.getUserByUsername(model.username());

        assertThat(testUser.username()).isEqualTo(profile.getUsername());
    }

    @Test
    void getUserByUsernameShouldThrowExceptionIfEmailIsEmpty() {
        assertThatThrownBy(() -> testService.getUserByUsername(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username is empty");

        verify(profileDao, never()).save(any(Profile.class));
    }

    @Test
    void getUserByUsernameShouldThrowExceptionIfUserDoesNotExist() {
        when(profileDao.findByUsername("ivan@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testService.getUserByUsername("ivan@gmail.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("Profile does not exist");

        verify(profileDao, never()).delete(any(Profile.class));
    }

}