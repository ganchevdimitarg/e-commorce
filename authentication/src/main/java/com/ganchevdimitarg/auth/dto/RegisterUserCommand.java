package com.ganchevdimitarg.auth.dto;

import com.ganchevdimitarg.auth.domain.UserRole;
import com.ganchevdimitarg.auth.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterUserCommand(
        @NotBlank @Email @Size(min = 5, max = 50) String email,
        @NotBlank @StrongPassword String password,
        @NotNull UserRole role,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String phoneNumber,
        @NotBlank String city,
        @NotBlank String street,
        @NotBlank String postCode) {}
