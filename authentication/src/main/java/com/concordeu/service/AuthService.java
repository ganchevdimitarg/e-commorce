package com.concordeu.service;

import com.concordeu.dto.UserDto;
import org.springframework.security.core.userdetails.UserDetailsService;

public interface AuthService extends UserDetailsService {
    UserDto register(UserDto model);
}
