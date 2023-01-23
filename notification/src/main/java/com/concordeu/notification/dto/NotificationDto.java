package com.concordeu.notification.dto;

public record NotificationDto(
		String recipient,
		String subject,
		String msgBody,
		String link
) {
}
