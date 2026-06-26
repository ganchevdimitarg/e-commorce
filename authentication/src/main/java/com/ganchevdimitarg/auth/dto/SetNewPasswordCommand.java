package com.ganchevdimitarg.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SetNewPasswordCommand(
        @NotBlank String email,
        @NotBlank String password) { }
