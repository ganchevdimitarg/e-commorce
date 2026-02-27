package com.ganchevdimitarg.notification.service;

import com.ganchevdimitarg.notification.dto.NotificationDto;

public interface EmailService {

    String sendSimpleMail(NotificationDto notificationDto);

    String sendMailWithAttachment(NotificationDto notificationDto);
}
