package com.concordeu.profile.service;

import com.concordeu.profile.dto.UserDto;
import com.concordeu.profile.dto.UserRequestDto;

public interface ProfileService {
    UserDto createAdmin(UserRequestDto model);
    UserDto createWorker(UserRequestDto model);
    UserDto createUser(UserRequestDto model);
    void updateUser(String email, UserRequestDto requestDto);
    void deleteUser(String email);
    UserDto getUserByUsername(String email);
    String passwordReset(String username);
    boolean isPasswordResetTokenValid(String token);
    void setNewPassword(String username, String password);
}
