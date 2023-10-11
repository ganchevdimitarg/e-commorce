package com.concordeu.profile.service;

import com.concordeu.profile.dto.UserDto;
import com.concordeu.client.common.dto.UserRequestDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import reactor.core.publisher.Mono;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public interface ProfileService {
    Mono<UserDto> createAdmin(UserRequestDto model);

    Mono<UserDto> createWorker(UserRequestDto model);

    Mono<UserDto> createUser(UserRequestDto userRequestDto) throws ExecutionException, InterruptedException, JsonProcessingException, TimeoutException;

    void updateUser(String username,
                    UserRequestDto userRequestDto);

    void deleteUser(String email) throws ExecutionException, InterruptedException, JsonProcessingException, TimeoutException;

    Mono<UserDto> getUserByUsername(String email) throws ExecutionException, InterruptedException, TimeoutException, JsonProcessingException;

    Mono<String> passwordReset(String username);

    boolean isPasswordResetTokenValid(String token);

    void setNewPassword(String username,
                        String password);
}
