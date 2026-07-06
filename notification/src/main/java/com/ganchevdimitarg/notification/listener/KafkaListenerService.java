package com.ganchevdimitarg.notification.listener;

import com.ganchevdimitarg.notification.config.KafkaTopics;
import com.ganchevdimitarg.notification.dto.NotificationDto;
import com.ganchevdimitarg.notification.service.EmailService;
import com.ganchevdimitarg.notification.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaListenerService {

    private final EmailService emailService;
    private final IdempotencyService idempotencyService;

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_EMAIL_REQUESTED,
            groupId = KafkaTopics.GROUP, containerFactory = "messageListener")
    public void onEmailRequested(@Payload NotificationDto dto,
                                 @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        String idempotencyKey = key != null ? key : dto.recipient() + ":" + dto.subject();
        idempotencyService.runOnce(idempotencyKey, () -> emailService.sendSimpleMail(dto));
    }
}
