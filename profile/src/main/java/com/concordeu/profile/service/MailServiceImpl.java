package com.concordeu.profile.service;

import com.concordeu.profile.dto.NotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailServiceImpl implements MailService {

    private final KafkaTemplate<String, NotificationDto> kafkaTemplate;

    @Override
    public void sendUserWelcomeMail(String username) {
        kafkaTemplate.send(
                "sentMail",
                new NotificationDto(
                        username,
                        "Registration",
                        "You have successfully registered. Please log in to your account."
                )
        );
        log.info("Email sent successfully");
    }
}
