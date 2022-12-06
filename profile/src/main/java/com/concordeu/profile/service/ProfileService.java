package com.concordeu.profile.service;

import com.concordeu.profile.dto.UserDto;
import com.concordeu.profile.dto.UserRequestDto;

public interface ProfileService {
    UserDto createAdmin(UserRequestDto model);
    UserDto createWorker(UserRequestDto model);
    UserDto createUser(UserRequestDto model);
    UserDto getOrCreateUser(String email);

    void updateUser(String email, UserRequestDto requestDto);

    void deleteUser(String email);

    UserDto getUserByUsername(String email);

}
