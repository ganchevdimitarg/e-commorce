package com.concordeu.notification.service;


import com.concordeu.client.common.dto.NotificationDto;

public interface NotificationService {
    NotificationDto createNotification(NotificationDto notification);
}
