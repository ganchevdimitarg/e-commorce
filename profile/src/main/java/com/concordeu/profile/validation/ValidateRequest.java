package com.concordeu.profile.validation;

import com.concordeu.profile.dto.UserRequestDto;

public interface ValidateRequest {
    boolean validateRequest(UserRequestDto requestDto);
}
