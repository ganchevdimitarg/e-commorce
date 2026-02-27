package com.ganchevdimitarg.profile.validation;

import com.ganchevdimitarg.profile.dto.UserRequestDto;

public interface ValidateData {
    boolean validateRequest(UserRequestDto requestDto);



    boolean isValidUsername(String username);
    boolean isValidPassword(String password);
}
