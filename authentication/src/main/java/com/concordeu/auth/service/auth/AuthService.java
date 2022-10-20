package com.concordeu.auth.service.auth;

import com.concordeu.auth.dto.AuthUserDto;
import com.concordeu.auth.dto.AuthUserRequestDto;

public interface AuthService {
    AuthUserDto createUser(AuthUserRequestDto model);
    AuthUserDto getOrCreateUser(String email);

    void updateUser(String email, AuthUserRequestDto requestDto);

    void deleteUser(String email);
}
