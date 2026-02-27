package com.ganchevdimitarg.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for creating or updating a user profile")
public record UserRequestDto(

        @NotBlank(message = "Username must not be blank")
        @Email(message = "Username must be a valid email. For example: example@gmail.com")
        @Size(min = 5, max = 50, message = "Username must be between 5 and 50 characters")
        String username,

        @NotBlank(message = "Password must not be blank")
        @Pattern(
                regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{6,30}$",
                message = "Password must contain at least one digit, one lowercase, one uppercase, one special character and no whitespace"
        )
        String password,

        @NotBlank(message = "First name must not be blank")
        @Pattern(
                regexp = "^[A-Z]\\p{L}{2,11}$",
                message = "First name must start with uppercase and contain only letters. For example: Ivan"
        )
        String firstName,

        @NotBlank(message = "Last name must not be blank")
        @Pattern(
                regexp = "^[A-Z]\\p{L}{2,11}$",
                message = "Last name must start with uppercase and contain only letters. For example: Ivanov"
        )
        String lastName,

        @NotBlank(message = "City must not be blank")
        String city,

        @NotBlank(message = "Street must not be blank")
        String street,

        @NotBlank(message = "Post code must not be blank")
        String postCode,

        @NotBlank(message = "Phone number must not be blank")
        @Pattern(
                regexp = "^(\\+\\d{1,3}( )?)?((\\(\\d{3}\\))|\\d{3})[- .]?\\d{3}[- .]?\\d{4}$",
                message = "Phone number is not correct. For example: +111 (202) 555-0125"
        )
        String phoneNumber,

        String cardNumber,
        String cardExpMonth,
        String cardExpYear,
        String cardCvc
) {}