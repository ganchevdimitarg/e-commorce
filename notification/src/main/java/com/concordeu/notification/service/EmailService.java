package com.concordeu.notification.service;

import com.concordeu.notification.dto.NotificationDTO;

public interface EmailService {

    String sendSimpleMail(NotificationDTO notificationDto);

    String sendMailWithAttachment(NotificationDTO notificationDto);
}
