package com.concordeu.mail.listener;

import com.concordeu.mail.dto.MailDto;
import com.concordeu.mail.service.EmailService;
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

    @KafkaListener(topics = "sentMail", groupId = "mail", containerFactory = "messageListener")
    public void listenToMessage(String message) throws JsonProcessingException {
        MailDto mailDto = MAPPER.readValue(message, MailDto.class);
        emailService.sendMailWithHTML(mailDto);
    }
}
