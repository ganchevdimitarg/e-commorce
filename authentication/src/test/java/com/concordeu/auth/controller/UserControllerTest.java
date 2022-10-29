package com.concordeu.auth.controller;

import com.concordeu.auth.config.jwt.JwtConfiguration;
import com.concordeu.auth.config.jwt.JwtSecretKey;
import com.concordeu.auth.dto.AuthUserDto;
import com.concordeu.auth.dto.AuthUserRequestDto;
import com.concordeu.auth.security.OAuth2UserSuccessHandler;
import com.concordeu.auth.service.auth.AuthService;
import com.concordeu.auth.service.securiy.SecurityService;
import com.concordeu.auth.util.JwtTokenUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithMockUser;
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
    AuthService authService;
    @MockBean
    PasswordEncoder passwordEncoder;
    @MockBean
    SecurityService securityService;
    @MockBean
    OAuth2UserSuccessHandler oAuth2UserSuccessHandler;
    @MockBean
    ClientRegistrationRepository clientRegistrationRepository;
    @MockBean
    JwtSecretKey jwtSecretKey;
    @MockBean
    JwtTokenUtil jwtTokenUtil;
    @MockBean
    JwtConfiguration jwtConfiguration;


    @Test
    @WithMockUser(username = "user", password = "user")
    void getUserByEmailShouldReturnUser() throws Exception {
        when(authService.getUserByEmail(any(String.class))).thenReturn(AuthUserDto.builder().build());
        mvc.perform(get("/api/v1/profile/get-by-email/{email}", "example@gmial.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "user", password = "user")
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

        verify(authService).createUser(any(AuthUserRequestDto.class));
    }

    @Test
    @WithMockUser(username = "user", password = "user")
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

        verify(authService).updateUser(any(String.class),any(AuthUserRequestDto.class));
    }

    @Test
    @WithMockUser(username = "user", password = "user")
    void deleteUserShouldDeleteUser() throws Exception {
        mvc.perform(delete("/api/v1/profile/delete/{email}", "ivan@gmail.com"))
                .andExpect(status().isOk());

        verify(authService).deleteUser(any(String.class));
    }
}
