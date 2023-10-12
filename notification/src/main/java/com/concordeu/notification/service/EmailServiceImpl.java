package com.concordeu.notification.service;


import com.concordeu.client.common.dto.NotificationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {
    private final JavaMailSender javaMailSender;
    private final NotificationService notificationService;

    @Value("${spring.mail.username}")
    private String sender;

    public String sendSimpleMail(NotificationDto notificationDto) {

        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(sender);
            mailMessage.setTo(notificationDto.recipient());
            mailMessage.setText(notificationDto.msgBody());
            mailMessage.setSubject(notificationDto.subject());
            javaMailSender.send(mailMessage);
            notificationService.createNotification(notificationDto);

            log.info("The email was successfully sent to: {}", notificationDto.recipient());
            return String.format("The email was successfully sent to: %s", notificationDto.recipient());

        } catch (MailException e) {
            log.warn(e.getMessage());
            throw new MailSendException(Objects.requireNonNull(e.getMessage()));
        }
    }

    public String sendMailWithAttachment(NotificationDto notificationDto) {
       /* MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper mimeMessageHelper;
        try {
            mimeMessageHelper = new MimeMessageHelper(mimeMessage, true);
            mimeMessageHelper.setFrom(sender);
            mimeMessageHelper.setTo(notificationDto.recipient());
            mimeMessageHelper.setText(notificationDto.msgBody());
            mimeMessageHelper.setSubject(notificationDto.subject());

            FileSystemResource file = new FileSystemResource(new File(notificationDto.attachment()));

            mimeMessageHelper.addAttachment(Objects.requireNonNull(file.getFilename()), file);
            javaMailSender.send(mimeMessage);

            log.info("The email was successfully sent to: {}", notificationDto.recipient());
            return String.format("The email was successfully sent to: %s", notificationDto.recipient());

        }
        catch (MessagingException e) {
            log.error(e.toString());
            throw new MailSendException(e.toString());
        }*/
        return "";
    }
}

