package com.ganchevdimitarg.profile.service;

import com.ganchevdimitarg.profile.dto.UserDto;
import com.ganchevdimitarg.profile.dto.UserRequestDto;
import reactor.core.publisher.Mono;

public interface ProfileService {

    Mono<UserDto> createAdmin(UserRequestDto userRequestDto);

    Mono<UserDto> createWorker(UserRequestDto userRequestDto);

    Mono<UserDto> createUser(UserRequestDto userRequestDto);

    Mono<Void> updateUser(String username, UserRequestDto userRequestDto);

    Mono<Void> deleteUser(String username);

    Mono<UserDto> getUserByUsername(String username);

    Mono<Void> passwordReset(String username);

    Mono<Boolean> isPasswordResetTokenValid(String token);

    Mono<Void> setNewPassword(String username, String password);
}