package com.ganchevdimitarg.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RequestPasswordResetCommand(
        @NotBlank @Email String email) { }
