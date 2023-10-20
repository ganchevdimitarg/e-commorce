package com.concordeu.notification.validation;


import com.concordeu.client.common.dto.NotificationDto;

public interface ValidateRequest {
    boolean validateRequest(NotificationDto notificationDto);
}
