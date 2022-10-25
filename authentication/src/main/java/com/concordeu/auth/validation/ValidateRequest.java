package com.concordeu.auth.validation;

import com.concordeu.auth.dto.AuthUserRequestDto;

public interface ValidateRequest {
    boolean validateRequest(AuthUserRequestDto requestDto);
}
