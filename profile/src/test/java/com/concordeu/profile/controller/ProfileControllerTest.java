package com.concordeu.profile.controller;

import com.concordeu.profile.dto.UserDto;
import com.concordeu.profile.dto.UserRequestDto;
import com.concordeu.profile.service.MailService;
import com.concordeu.profile.service.ProfileService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.HashSet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

@WebFluxTest(controllers = ProfileController.class)
@ActiveProfiles("test")
@Tag("integration")
class ProfileControllerTest {
    @Autowired
    WebTestClient client;
    @MockBean
    ProfileService profileService;
    @MockBean
    PasswordEncoder passwordEncoder;
    @MockBean
    MailService mailService;

/*    @Test
    @Disabled
    void getUserByEmailShouldReturnUser() {
        when(profileService.getUserByUsername(any(String.class))).thenReturn(new UserDto(
                "",
                "",
                "",
                new HashSet<>(),
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                0,
                0,
                "",
                ""));
        this.client.mutateWith(mockUser("admin"))
                .get()
                .uri("/api/v1/profile/get-by-username?username={email}", "example@gmail.com")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @Disabled
    void registerUserShouldCreateUser() {
        this.client.mutateWith(csrf())
                .mutateWith(mockUser("admin"))
                .post()
                .uri("/api/v1/profile/register-user")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                            "username" : "ivanIvanov",
                            "password" : "Abc123!@#",
                            "firstName" : "Ivanь",
                            "lastName" : "Ivanov",
                            "email" : "ivan@gmail.com",
                            "phoneNumber" : "0888888888",
                            "city" : "varna",
                            "street" : "katay",
                            "postCode" : "9000"
                        }
                        """)
                .exchange()
                .expectStatus().isOk();

        verify(profileService).createUser(any(UserRequestDto.class));
    }

    @Test
    @Disabled
    void updateUserShouldUpdateUserInfo() {
        this.client.mutateWith(csrf())
                .mutateWith(mockUser("admin"))
                .put()
                .uri("/api/v1/profile/update-user?username={email}", "ivan@gmail.com")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                            "username" : "dddddd",
                            "password" : "Abc123!@#",
                            "firstName" : "Ivanь",
                            "lastName" : "Ivanov",
                            "email" : "ivan@gmail.com",
                            "phoneNumber" : "0888888888",
                            "city" : "varna",
                            "street" : "katay",
                            "postCode" : "9000"
                        }
                        """)
                .exchange()
                .expectStatus().isOk();

        verify(profileService).updateUser(any(String.class), any(UserRequestDto.class));
    }

    @Test
    @Disabled
    void deleteUserShouldDeleteUser() {
        this.client.mutateWith(csrf())
                .mutateWith(mockUser("admin"))
                .delete()
                .uri("/api/v1/profile/delete-user?username={email}", "ivan@gmail.com")
                .exchange()
                .expectStatus().isOk();

        verify(profileService).deleteUser(any(String.class));
    }*/
}
