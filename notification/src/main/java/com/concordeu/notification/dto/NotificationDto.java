package com.ganchevdimitarg.notification.dto;

public record NotificationDto(
        String recipient,
        String subject,
        String msgBody) {
}
