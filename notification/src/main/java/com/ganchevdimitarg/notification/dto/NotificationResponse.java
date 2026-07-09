package com.ganchevdimitarg.notification.dto;

import java.time.LocalDateTime;

public record NotificationResponse(String id, String recipient, String status, LocalDateTime sentAt) {
}
