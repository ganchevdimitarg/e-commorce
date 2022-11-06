package com.concordeu.profile.service.auth;

import com.concordeu.profile.dto.UserDto;
import com.concordeu.profile.dto.UserRequestDto;

public interface ProfileService {
    UserDto createUser(UserRequestDto model);
    UserDto getOrCreateUser(String email);

    void updateUser(String email, UserRequestDto requestDto);

    void deleteUser(String email);

    UserDto getUserByEmail(String email);

}
