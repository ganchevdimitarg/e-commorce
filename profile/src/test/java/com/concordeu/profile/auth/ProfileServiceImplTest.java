package com.concordeu.profile.auth;

import com.concordeu.profile.dao.UserDao;
import com.concordeu.profile.domain.User;
import com.concordeu.profile.dto.UserDto;
import com.concordeu.profile.dto.UserRequestDto;
import com.concordeu.profile.excaption.InvalidRequestDataException;
import com.concordeu.profile.mapper.MapStructMapper;
import com.concordeu.profile.service.auth.ProfileService;
import com.concordeu.profile.service.auth.ProfileServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    ProfileService testService;

    @Mock
    UserDao userDao;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    MapStructMapper mapper;

    UserRequestDto model;
    @BeforeEach
    void setUp() {
        testService = new ProfileServiceImpl(userDao, passwordEncoder, mapper);
        model = new UserRequestDto(
                "example@gmail.com",
                "Abc123!@#",
                "Ivan",
                "Ivanov",
                "0888888888",
                "Varna",
                "Katay",
                "9000");
    }

    @Test
    void createUserShouldCreateUserIfUserNotExist() {
        when(userDao.findByUsername(model.username())).thenReturn(Optional.empty());

        testService.createUser(model);

        ArgumentCaptor<User> argumentCaptor = ArgumentCaptor.forClass(User.class);
        verify(userDao).insert(argumentCaptor.capture());

        User captorUser = argumentCaptor.getValue();
        assertThat(captorUser).isNotNull();
        assertThat(captorUser.getUsername()).isEqualTo(model.username());
    }

    @Test
    void createUserShouldThrowExceptionIfUserExist() {
        when(userDao.findByUsername(model.username())).thenReturn(Optional.of(User.builder().build()));

        assertThatThrownBy(() -> testService.createUser(model))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining("User already exist: " + model.username());

        verify(userDao, never()).insert(any(User.class));
    }

    @Test
    void getOrCreateUserShouldReturnUserIfExist() {
        String email = "example@gmail.com";
        User user = User.builder().username(email).build();
        when(userDao.findByUsername(email)).thenReturn(Optional.of(user));
        when(mapper.mapAuthUserToAuthUserDto(any(User.class))).thenReturn(UserDto.builder().email(email).build());

        UserDto userFromDataBase = testService.getOrCreateUser(email);

        assertThat(userFromDataBase.getUsername()).isEqualTo(user.getUsername());
    }

    @Test
    void getOrCreateUserShouldThrowExceptionIfEmailIsEmpty() {
        String email = "";

        assertThatThrownBy(() -> testService.getOrCreateUser(email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email is empty");

        verify(userDao, never()).findByUsername(any(String.class));
    }

    @Test
    void updateUserShouldUpdateUserInfo() {
        User userBefore = User.builder().firstName("Dimitar").build();
        when(userDao.findByUsername(model.username())).thenReturn(Optional.of(userBefore));

        testService.updateUser(model.username(), model);

        ArgumentCaptor<User> argumentCaptor = ArgumentCaptor.forClass(User.class);
        verify(userDao).save(argumentCaptor.capture());

        User userAfter = argumentCaptor.getValue();
        assertThat(userAfter).isNotNull();
        assertThat(userAfter).isEqualTo(userBefore);
        assertThat(userAfter.getUsername()).isEqualTo(model.username());
    }

    @Test
    void updateUserShouldThrowExceptionIfEmailIsEmpty() {
        assertThatThrownBy(() -> testService.updateUser("", model))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email is empty");

        verify(userDao, never()).save(any(User.class));
    }

    @Test
    void updateUserShouldThrowExceptionIfUserDoesNotExist() {
        when(userDao.findByUsername("ivan@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testService.updateUser("ivan@gmail.com", model))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User does not exist");

        verify(userDao, never()).save(any(User.class));
    }

    @Test
    void deleteUserShouldDeleteUser() {
        when(userDao.findByUsername(model.username())).thenReturn(Optional.of(User.builder().build()));

        testService.deleteUser(model.username());

        verify(userDao).delete(any(User.class));
    }

    @Test
    void deleteUserShouldThrowExceptionIfEmailIsEmpty() {
        assertThatThrownBy(() -> testService.deleteUser(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email is empty");

        verify(userDao, never()).save(any(User.class));
    }

    @Test
    void deleteUserShouldThrowExceptionIfUserDoesNotExist() {
        when(userDao.findByUsername("ivan@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testService.deleteUser("ivan@gmail.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User does not exist");

        verify(userDao, never()).delete(any(User.class));
    }

    @Test
    void getUserByEmailShouldReturnUser() {
        User user = User.builder().username(model.username()).build();
        when(userDao.findByUsername(model.username())).thenReturn(Optional.of(user));
        when(mapper.mapAuthUserToAuthUserDto(user)).thenReturn(UserDto.builder().email(model.username()).build());

        UserDto testUser = testService.getUserByEmail(model.username());

        assertThat(testUser.getUsername()).isEqualTo(user.getUsername());
    }

    @Test
    void getUserByEmailShouldThrowExceptionIfEmailIsEmpty() {
        assertThatThrownBy(() -> testService.getUserByEmail(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email is empty");

        verify(userDao, never()).save(any(User.class));
    }

    @Test
    void getUserByEmailShouldThrowExceptionIfUserDoesNotExist() {
        when(userDao.findByUsername("ivan@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testService.getUserByEmail("ivan@gmail.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User does not exist");

        verify(userDao, never()).delete(any(User.class));
    }

}