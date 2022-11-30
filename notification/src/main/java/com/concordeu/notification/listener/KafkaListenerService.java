package com.concordeu.notification.listener;

import com.concordeu.notification.dto.NotificationDto;
import com.concordeu.notification.service.EmailService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaListenerService {
    private final EmailService emailService;
    private final ObjectMapper mapper;

    @KafkaListener(topics = "sentMail", groupId = "notification", containerFactory = "messageListener")
    public void listenToMessage(String message) throws JsonProcessingException {
        NotificationDto notificationDto = mapper.readValue(message, NotificationDto.class);
        emailService.sendSimpleMail(notificationDto);
    }
}
