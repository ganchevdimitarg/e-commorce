package com.ganchevdimitarg.notification.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.notification.dto.NotificationDto;
import com.ganchevdimitarg.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaListenerService {

    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    @RetryableTopic(
            attempts = "4",
            backOff = @BackOff(delay = 1_000, multiplier = 2.0),
            retryTopicSuffix = ".retry",
            dltTopicSuffix = ".DLT")
    @KafkaListener(topics = "order.notification.requested", groupId = "notification-group",
            containerFactory = "messageListener")
    public void listenToMessage(String message) throws JsonProcessingException {
        NotificationDto notificationDto = objectMapper.readValue(message, NotificationDto.class);
        emailService.sendSimpleMail(notificationDto);
    }

    @DltHandler
    public void handleDlt(String message, @Header(KafkaHeaders.ORIGINAL_TOPIC) String originalTopic) {
        // Dead-lettered after all retries: keep the payload in the log for manual replay.
        log.error("Notification dead-lettered from {}: {}", originalTopic, message);
    }
}
