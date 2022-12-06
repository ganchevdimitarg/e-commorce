package com.concordeu.order.dto;

public record NotificationDto(
        String recipient,
        String subject,
        String msgBody) {
}
