package com.ganchevdimitarg.profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Notification message payload sent via Kafka")
public record NotificationDto(
        @Schema(description = "Recipient username or email", example = "user@example.com")
        String recipient,

        @Schema(description = "Email subject line", example = "Registration")
        String subject,

        @Schema(description = "Email body content")
        String body
) {}
