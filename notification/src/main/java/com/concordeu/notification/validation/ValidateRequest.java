package com.concordeu.notification.validation;

import com.concordeu.notification.dto.NotificationDto;

public interface ValidateRequest {
    boolean validateRequest(NotificationDto notificationDto);
}
