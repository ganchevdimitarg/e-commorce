package com.ganchevdimitarg.notification.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.notification.dto.NotificationDto;
import com.ganchevdimitarg.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaListenerService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final EmailService emailService;

    // DLT/@RetryableTopic deferred to the notification Boot-4 migration — the module's
    // pre-Boot-4 spring-kafka has no RetryableTopic and no spring-retry on the classpath
    // (see order/decisions.md, 2026-07-06 follow-up).
    @KafkaListener(topics = "order.notification.requested", groupId = "notification-group", containerFactory = "messageListener")
    public void listenToMessage(String message) throws JsonProcessingException {
        NotificationDto notificationDto = MAPPER.readValue(message, NotificationDto.class);
        emailService.sendSimpleMail(notificationDto);
    }
}
