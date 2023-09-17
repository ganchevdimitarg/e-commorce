package com.concordeu.profile.service;

import com.concordeu.profile.dto.UserDto;
import com.concordeu.profile.dto.UserRequestDto;
import reactor.core.publisher.Mono;

public interface ProfileService {
    Mono<UserDto> createAdmin(UserRequestDto model);

    Mono<UserDto> createWorker(UserRequestDto model);

    Mono<UserDto> createUser(UserRequestDto userRequestDto);

    void updateUser(String username,
                    UserRequestDto userRequestDto);

    void deleteUser(String email);

    Mono<UserDto> getUserByUsername(String email);

    Mono<String> passwordReset(String username);

    boolean isPasswordResetTokenValid(String token);

    void setNewPassword(String username,
                        String password);
}
