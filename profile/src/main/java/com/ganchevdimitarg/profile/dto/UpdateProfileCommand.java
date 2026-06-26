package com.ganchevdimitarg.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Request payload for updating the authenticated user's profile")
public record UpdateProfileCommand(

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
                regexp = "^(\\+\\d{1,3}( )?)?((\\(\\d{3}\\))|\\d{3})[- .]?\\d{3}[- .]?\\d{4}$|^[0-9]{9,10}$",
                message = "Phone number is not correct. For example: +111 (202) 555-0125"
        )
        String phoneNumber
) {
}
