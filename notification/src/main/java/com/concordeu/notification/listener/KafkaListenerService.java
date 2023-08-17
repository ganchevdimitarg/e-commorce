package com.concordeu.notification.listener;

import com.concordeu.notification.dto.NotificationDTO;
import com.concordeu.notification.service.EmailService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaListenerService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final EmailService emailService;

    @KafkaListener(topics = "sentMail", groupId = "notification", containerFactory = "messageListener")
    public void listenToMessage(String message) throws JsonProcessingException {
        NotificationDTO notificationDto = MAPPER.readValue(message, NotificationDTO.class);
        emailService.sendSimpleMail(notificationDto);
    }
}
