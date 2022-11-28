package com.concordeu.notification.listener;

import com.concordeu.notification.dto.NotificationDto;
import com.concordeu.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaListenerService {
    private final EmailService emailService;

    @KafkaListener(topics = "welcomeMail", groupId = "notification")
    public void listenWelcomeMail(NotificationDto notificationDto) {
        emailService.sendSimpleMail(notificationDto);
        log.info("Welcome Email will be sent");
    }

    @KafkaListener(topics = "orderMail", groupId = "notification")
    public void listenOrderMail(NotificationDto notificationDto) {
        emailService.sendSimpleMail(notificationDto);
        log.info("Order Email will be sent");
    }
}
