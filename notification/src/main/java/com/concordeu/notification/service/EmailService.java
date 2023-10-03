package com.concordeu.notification.service;

import com.concordeu.notification.dto.NotificationDto;

public interface EmailService {

    String sendSimpleMail(NotificationDto notificationDto);

    String sendMailWithAttachment(NotificationDto notificationDto);
}
