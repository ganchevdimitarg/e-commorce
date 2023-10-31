package com.concordeu.profile.service;

import com.concordeu.profile.dto.UserDto;
import com.concordeu.client.common.dto.UserRequestDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import reactor.core.publisher.Mono;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public interface ProfileService {
    UserDto createWorker(UserRequestDto model);

    UserDto createUser(UserRequestDto userRequestDto) throws ExecutionException, InterruptedException, JsonProcessingException, TimeoutException;

    void updateUser(String username,
                    UserRequestDto userRequestDto);

    void deleteUser(String email) throws ExecutionException, InterruptedException, JsonProcessingException, TimeoutException;

    UserDto getUserByUsername(String email) throws ExecutionException, InterruptedException, TimeoutException, JsonProcessingException;

    String passwordReset(String username);

    boolean isPasswordResetTokenValid(String token);

    void setNewPassword(String username,
                        String password);
}
