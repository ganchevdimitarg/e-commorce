package com.ganchevdimitarg.profile.service;

import com.ganchevdimitarg.profile.dto.NotificationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class MailServiceImpl implements MailService {

    private final KafkaTemplate<String, NotificationDto> kafkaTemplate;
    private final String mailTopic;

    public MailServiceImpl(
            KafkaTemplate<String, NotificationDto> kafkaTemplate,
            @Value("${kafka.topics.mail}") String mailTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.mailTopic = mailTopic;
    }

    /**
     * Sends a welcome email notification to a newly registered user via Kafka.
     * The message is published to the configured mail topic and processed
     * asynchronously by the notification service.
     *
     * @param username the username (email) of the newly registered user
     * @return a {@link Mono} completing empty on successful publish
     * @throws org.apache.kafka.common.KafkaException if the message cannot be published
     */
    @Override
    public Mono<Void> sendUserWelcomeMail(String username) {
        return Mono.fromFuture(kafkaTemplate.send(
                        mailTopic,
                        new NotificationDto(
                                username,
                                "Registration",
                                "You have successfully registered. Please log in to your account."
                        )))
                .doOnSuccess(result -> log.info(
                        "Welcome email notification sent for user {} at offset [{}]",
                        username, result.getRecordMetadata().offset()))
                .doOnError(ex -> log.error(
                        "Failed to send welcome email notification for user {}: {}",
                        username, ex.getMessage()))
                .then();
    }

    /**
     * Sends a password reset token to the specified user via Kafka.
     * The token is delivered as the message body and must be handled
     * securely by the downstream notification service.
     *
     * @param username the username (email) of the user requesting a password reset
     * @param token    the generated JWT password reset token
     * @return a {@link Mono} completing empty on successful publish
     * @throws org.apache.kafka.common.KafkaException if the message cannot be published
     */
    @Override
    public Mono<Void> sendPasswordResetTokenMail(String username, String token) {
        return Mono.fromFuture(kafkaTemplate.send(
                        mailTopic,
                        new NotificationDto(
                                username,
                                "Password reset token",
                                token
                        )))
                .doOnSuccess(result -> log.info(
                        "Password reset email notification sent for user {} at offset [{}]",
                        username, result.getRecordMetadata().offset()))
                .doOnError(ex -> log.error(
                        "Failed to send password reset email notification for user {}: {}",
                        username, ex.getMessage()))
                .then();
    }
}