package com.concordeu.auth.service.auth;

import com.concordeu.auth.dao.AuthUserDao;
import com.concordeu.auth.domain.AuthUser;
import com.concordeu.auth.dto.AuthUserDto;
import com.concordeu.auth.dto.AuthUserRequestDto;
import com.concordeu.auth.excaption.InvalidRequestDataException;
import com.concordeu.auth.mapper.MapStructMapper;
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
class AuthServiceImplTest {

    AuthService testService;

    @Mock
    AuthUserDao authUserDao;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    MapStructMapper mapper;

    AuthUserRequestDto model;
    @BeforeEach
    void setUp() {
        testService = new AuthServiceImpl(authUserDao, passwordEncoder, mapper);
        model = new AuthUserRequestDto("ivanIvanov", "Abc123!@#",
                "Ivan", "Ivanov", "example@gmail.com",
                "0888888888", "Varna", "Katay", "9000");
    }

    @Test
    void createUserShouldCreateUserIfUserNotExist() {
        when(authUserDao.findByEmail(model.email())).thenReturn(Optional.empty());

        testService.createUser(model);

        ArgumentCaptor<AuthUser> argumentCaptor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserDao).insert(argumentCaptor.capture());

        AuthUser captorUser = argumentCaptor.getValue();
        assertThat(captorUser).isNotNull();
        assertThat(captorUser.getEmail()).isEqualTo(model.email());
    }

    @Test
    void createUserShouldThrowExceptionIfUserExist() {
        when(authUserDao.findByEmail(model.email())).thenReturn(Optional.of(AuthUser.builder().build()));

        assertThatThrownBy(() -> testService.createUser(model))
                .isInstanceOf(InvalidRequestDataException.class)
                .hasMessageContaining("User already exist: " + model.email());

        verify(authUserDao, never()).insert(any(AuthUser.class));
    }

    @Test
    void getOrCreateUserShouldReturnUserIfExist() {
        String email = "example@gmail.com";
        AuthUser user = AuthUser.builder().email(email).build();
        when(authUserDao.findByEmail(email)).thenReturn(Optional.of(user));
        when(mapper.mapAuthUserToAuthUserDto(any(AuthUser.class))).thenReturn(AuthUserDto.builder().email(email).build());

        AuthUserDto userFromDataBase = testService.getOrCreateUser(email);

        assertThat(userFromDataBase.getEmail()).isEqualTo(user.getEmail());
    }

    @Test
    void getOrCreateUserShouldThrowExceptionIfEmailIsEmpty() {
        String email = "";

        assertThatThrownBy(() -> testService.getOrCreateUser(email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email is empty");

        verify(authUserDao, never()).findByEmail(any(String.class));
    }

    @Test
    void updateUserShouldUpdateUserInfo() {
        AuthUser userBefore = AuthUser.builder().firstName("Dimitar").build();
        when(authUserDao.findByEmail(model.email())).thenReturn(Optional.of(userBefore));

        testService.updateUser(model.email(), model);

        ArgumentCaptor<AuthUser> argumentCaptor = ArgumentCaptor.forClass(AuthUser.class);
        verify(authUserDao).save(argumentCaptor.capture());

        AuthUser userAfter = argumentCaptor.getValue();
        assertThat(userAfter).isNotNull();
        assertThat(userAfter).isEqualTo(userBefore);
        assertThat(userAfter.getEmail()).isEqualTo(model.email());
    }

    @Test
    void updateUserShouldThrowExceptionIfEmailIsEmpty() {
        assertThatThrownBy(() -> testService.updateUser("", model))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email is empty");

        verify(authUserDao, never()).save(any(AuthUser.class));
    }

    @Test
    void updateUserShouldThrowExceptionIfUserDoesNotExist() {
        when(authUserDao.findByEmail("ivan@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testService.updateUser("ivan@gmail.com", model))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User does not exist");

        verify(authUserDao, never()).save(any(AuthUser.class));
    }

    @Test
    void deleteUserShouldDeleteUser() {
        when(authUserDao.findByEmail(model.email())).thenReturn(Optional.of(AuthUser.builder().build()));

        testService.deleteUser(model.email());

        verify(authUserDao).delete(any(AuthUser.class));
    }

    @Test
    void deleteUserShouldThrowExceptionIfEmailIsEmpty() {
        assertThatThrownBy(() -> testService.deleteUser(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email is empty");

        verify(authUserDao, never()).save(any(AuthUser.class));
    }

    @Test
    void deleteUserShouldThrowExceptionIfUserDoesNotExist() {
        when(authUserDao.findByEmail("ivan@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testService.deleteUser("ivan@gmail.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User does not exist");

        verify(authUserDao, never()).delete(any(AuthUser.class));
    }

    @Test
    void getUserByEmailShouldReturnUser() {
        AuthUser user = AuthUser.builder().email(model.email()).build();
        when(authUserDao.findByEmail(model.email())).thenReturn(Optional.of(user));
        when(mapper.mapAuthUserToAuthUserDto(user)).thenReturn(AuthUserDto.builder().email(model.email()).build());

        AuthUserDto testUser = testService.getUserByEmail(model.email());

        assertThat(testUser.getEmail()).isEqualTo(user.getEmail());
    }

    @Test
    void getUserByEmailShouldThrowExceptionIfEmailIsEmpty() {
        assertThatThrownBy(() -> testService.getUserByEmail(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email is empty");

        verify(authUserDao, never()).save(any(AuthUser.class));
    }

    @Test
    void getUserByEmailShouldThrowExceptionIfUserDoesNotExist() {
        when(authUserDao.findByEmail("ivan@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testService.getUserByEmail("ivan@gmail.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User does not exist");

        verify(authUserDao, never()).delete(any(AuthUser.class));
    }

}