package com.concordeu.notification.validation;

import com.concordeu.notification.dto.NotificationDTO;

public interface ValidateRequest {
    boolean validateRequest(NotificationDTO notificationDto);
}
