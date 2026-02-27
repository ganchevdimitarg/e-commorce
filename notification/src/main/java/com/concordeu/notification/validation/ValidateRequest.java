package com.ganchevdimitarg.notification.validation;

import com.ganchevdimitarg.notification.dto.NotificationDto;

public interface ValidateRequest {
    boolean validateRequest(NotificationDto notificationDto);
}
