package com.concordeu.profile.validation;

import com.concordeu.client.common.dto.UserRequestDto;

public interface ValidateData {
    boolean validateRequest(UserRequestDto requestDto);
    boolean isValidUsername(String username);
    boolean isValidPassword(String password);
}
