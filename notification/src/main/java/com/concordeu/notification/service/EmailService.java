package com.concordeu.notification.service;


import com.concordeu.client.common.dto.NotificationDto;

public interface EmailService {

    String sendSimpleMail(NotificationDto notificationDto);

    String sendMailWithAttachment(NotificationDto notificationDto);
}
