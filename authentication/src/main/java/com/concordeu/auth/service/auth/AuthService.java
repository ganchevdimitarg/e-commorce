package com.concordeu.auth.service.auth;

import com.concordeu.auth.dto.AuthUserDto;
import com.concordeu.auth.dto.AuthUserRequestDto;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public interface AuthService {
    AuthUserDto createUser(AuthUserRequestDto model);
    AuthUserDto getOrCreateUser(String email);

    void updateUser(String email, AuthUserRequestDto requestDto);

    void deleteUser(String email);

    AuthUserDto getUserByEmail(String email);

    void refreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException;
}
