package com.concordeu.profile.controller;

import com.concordeu.profile.dto.UserDto;
import com.concordeu.profile.dto.UserRequestDto;
import com.concordeu.profile.service.auth.ProfileService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(UserController.class)
@Import(UserController.class)
@Tag("integration")
class UserControllerTest {
    @Autowired
    MockMvc mvc;

    @MockBean
    ProfileService profileService;
    @MockBean
    PasswordEncoder passwordEncoder;

    @Test
    void getUserByEmailShouldReturnUser() throws Exception {
        when(profileService.getUserByEmail(any(String.class))).thenReturn(UserDto.builder().build());
        mvc.perform(get("/api/v1/profile/get-by-email/{email}", "example@gmial.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void registerUserShouldCreateUser() throws Exception {
        mvc.perform(post("/api/v1/profile/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
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
                                """))
                .andExpect(status().isOk());

        verify(profileService).createUser(any(UserRequestDto.class));
    }

    @Test
    void updateUserShouldUpdateUserInfo() throws Exception {
        mvc.perform(put("/api/v1/profile/update/{email}", "ivan@gmail.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
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
                                """))
                .andExpect(status().isOk());

        verify(profileService).updateUser(any(String.class),any(UserRequestDto.class));
    }

    @Test
    void deleteUserShouldDeleteUser() throws Exception {
        mvc.perform(delete("/api/v1/profile/delete/{email}", "ivan@gmail.com"))
                .andExpect(status().isOk());

        verify(profileService).deleteUser(any(String.class));
    }
}
