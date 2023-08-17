package com.concordeu.profile.dto;

public record NotificationDTO(
        String recipient,
        String subject,
        String msgBody) {
}
