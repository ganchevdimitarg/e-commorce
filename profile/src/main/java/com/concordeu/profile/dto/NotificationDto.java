package com.concordeu.profile.dto;

public record NotificationDto(
        String recipient,
        String subject,
        String msgBody,
        String attachment) {
}
