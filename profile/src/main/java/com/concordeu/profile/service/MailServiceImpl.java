package com.concordeu.profile.service;

import com.concordeu.profile.dto.NotificationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailServiceImpl implements MailService {
    private final KafkaTemplate<String, NotificationDTO> kafkaTemplate;

    @Override
    public void sendUserWelcomeMail(String username) {
        kafkaTemplate.send(
                "sentMail",
                new NotificationDTO(
                        username,
                        "Registration",
                        "You have successfully registered. Please log in to your account."
                )
        );
        log.info("Register email sent successfully");
    }

    @Override
    public void sendPasswordResetTokenMail(String username, String token) {
        kafkaTemplate.send(
                "sentMail",
                new NotificationDTO(
                        username,
                        "Password reset token",
                        String.format("""
                                Please click on the URL below to set a new password.
                                token: %s
                                """, token)
                )
        );
        log.info("Password reset token email sent successfully");
    }
}
