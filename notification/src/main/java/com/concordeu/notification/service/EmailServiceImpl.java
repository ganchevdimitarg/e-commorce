package com.concordeu.notification.service;

import com.concordeu.notification.dto.NotificationDto;

import java.io.File;
import java.util.Objects;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {
    private final JavaMailSender javaMailSender;
    private final NotificationService notificationService;

    @Value("${spring.mail.username}")
    private String sender;

    public String sendSimpleMail(NotificationDto notificationDto) {

        // Try block to check for exceptions
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom(sender);
            mailMessage.setTo(notificationDto.recipient());
            mailMessage.setText(notificationDto.msgBody());
            mailMessage.setSubject(notificationDto.subject());
            javaMailSender.send(mailMessage);
            notificationService.createNotification(notificationDto);

            log.info(String.format("Mail Sent Successfully to: %s", notificationDto.recipient()));
            return String.format("Mail Sent Successfully to: %s", notificationDto.recipient());

        } catch (MailException e) {
            log.error(e.toString());
            throw new MailSendException(e.toString());
        }
    }

    public String sendMailWithAttachment(NotificationDto notificationDto) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
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

            log.info(String.format("Mail Sent Successfully to: %s", notificationDto.recipient()));
            return String.format("Mail Sent Successfully to: %s", notificationDto.recipient());

        }
        catch (MessagingException e) {
            log.error(e.toString());
            throw new MailSendException(e.toString());
        }
    }
}

