package com.ganchevdimitarg.notification.service;

import com.ganchevdimitarg.notification.dto.NotificationDto;
import com.ganchevdimitarg.notification.dto.NotificationResponse;

public interface EmailService {

    NotificationResponse sendSimpleMail(NotificationDto notificationDto);
}
