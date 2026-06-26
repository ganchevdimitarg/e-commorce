package com.ganchevdimitarg.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Schema(description = "User profile response payload")
@Builder
public record UserDto(
        @Schema(description = "Shared user identifier (auth subject)", example = "9f1c...")
        String userId,

        @Schema(description = "First name", example = "Test")
        String firstName,

        @Schema(description = "Last name", example = "Testov")
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
