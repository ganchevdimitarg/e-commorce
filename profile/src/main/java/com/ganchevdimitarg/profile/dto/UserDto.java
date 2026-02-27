package com.ganchevdimitarg.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.util.Set;

@Schema(description = "User profile response payload")
@Builder
public record UserDto(
        @Schema(description = "Profile document ID")
        String id,

        @Schema(description = "User email address used as login", example = "user@example.com")
        String username,

        @Schema(description = "Always returned empty — never expose encoded password")
        String password,

        @Schema(description = "Granted authority names e.g. ROLE_USER, profile.read")
        Set<String> grantedAuthorities,

        @Schema(description = "First name", example = "Ivan")
        String firstName,

        @Schema(description = "Last name", example = "Ivanov")
        String lastName,

        @Schema(description = "Phone number", example = "+359888000111")
        String phoneNumber,

        @Schema(description = "City", example = "Varna")
        String city,

        @Schema(description = "Street", example = "Main St")
        String street,

        @Schema(description = "Post code", example = "9000")
        String postCode,

        @Schema(description = "Payment card reference ID", example = "card_456")
        String cardId
) {}