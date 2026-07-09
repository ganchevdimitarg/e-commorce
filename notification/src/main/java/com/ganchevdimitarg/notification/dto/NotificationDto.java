package com.ganchevdimitarg.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NotificationDto(
        @NotBlank @Email String recipient,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(min = 10, max = 251) String msgBody) {
}
