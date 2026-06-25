package com.ganchevdimitarg.order.dto;

public record NotificationDto(
        String recipient,
        String subject,
        String msgBody) {
}
