package com.ganchevdimitarg.notification.service;

import com.ganchevdimitarg.notification.dto.NotificationDto;
import com.ganchevdimitarg.notification.dto.NotificationResponse;
import com.ganchevdimitarg.notification.exception.MailDeliveryException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender javaMailSender;
    private final NotificationService notificationService;

    @Value("${spring.mail.username:no-reply@e-commerce.local}")
    private String sender;

    @Override
    public NotificationResponse sendSimpleMail(NotificationDto notificationDto) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(sender);
            mailMessage.setTo(notificationDto.recipient());
            mailMessage.setSubject(notificationDto.subject());
            mailMessage.setText(notificationDto.msgBody());
            javaMailSender.send(mailMessage);

            NotificationDto saved = notificationService.createNotification(notificationDto);
            log.info("Email successfully sent to {}", notificationDto.recipient());
            return new NotificationResponse(null, saved.recipient(), "SENT", LocalDateTime.now());
        } catch (MailException e) {
            log.warn("Email delivery failed for {}: {}", notificationDto.recipient(), e.getMessage());
            throw new MailDeliveryException(notificationDto.recipient());
        }
    }
}
