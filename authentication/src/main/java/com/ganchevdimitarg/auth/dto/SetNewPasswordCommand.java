package com.ganchevdimitarg.auth.dto;

import com.ganchevdimitarg.auth.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

public record SetNewPasswordCommand(
        @NotBlank String token,
        @NotBlank @StrongPassword String password) { }
