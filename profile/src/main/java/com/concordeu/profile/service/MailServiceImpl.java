package com.concordeu.profile.service;

import com.concordeu.profile.dto.NotificationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
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
    }
}
