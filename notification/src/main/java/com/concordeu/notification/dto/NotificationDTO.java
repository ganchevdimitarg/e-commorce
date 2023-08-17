package com.concordeu.notification.dto;

public record NotificationDTO(
        String recipient,
        String subject,
        String msgBody) {
}
