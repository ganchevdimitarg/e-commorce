package com.concordeu.service;

import com.concordeu.dto.AuthUserDto;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface AuthService extends UserDetailsService {
    AuthUserDto createUser(AuthUserDto model);
    AuthUserDto getOrCreateUser(String email);
}
