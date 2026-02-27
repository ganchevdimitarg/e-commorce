package com.ganchevdimitarg.profile.mailService;

import com.ganchevdimitarg.profile.dto.NotificationDto;
import com.ganchevdimitarg.profile.service.MailServiceImpl;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SendPasswordResetTokenMailTest {

    @Mock
    private KafkaTemplate<String, NotificationDto> kafkaTemplate;

    private MailServiceImpl mailService;

    @BeforeEach
    void setUp() {
        mailService = new MailServiceImpl(kafkaTemplate, "sentMail");
    }

    @Test
    void sendPasswordResetTokenMail_happyPath_completesAndLogsOffset() {
        // Arrange
        String username = "user@example.com";
        String token = "reset-token-xyz";
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("sentMail", 0), 10L, 0, 0L, 0, 0);
        SendResult<String, NotificationDto> sendResult = mock(SendResult.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(kafkaTemplate.send(eq("sentMail"), any(NotificationDto.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        // Act
        Mono<Void> result = mailService.sendPasswordResetTokenMail(username, token);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        // Verifies sent mail DTO contains correct username, subject, and body
        verify(kafkaTemplate).send(eq("sentMail"), argThat(dto ->
                dto.recipient().equals(username) &&
                        dto.subject().equals("Password reset token") &&
                        dto.body().equals(token)));
    }

    @Test
    void sendPasswordResetTokenMail_kafkaFailure_propagatesError() {
        // Arrange
        CompletableFuture<SendResult<String, NotificationDto>> failedFuture =
                CompletableFuture.failedFuture(new RuntimeException("Kafka broker unavailable"));
        when(kafkaTemplate.send(eq("sentMail"), any(NotificationDto.class)))
                .thenReturn(failedFuture);

        // Act
        Mono<Void> result = mailService.sendPasswordResetTokenMail("user@example.com", "token");

        // Assert
        StepVerifier.create(result)
                .expectErrorMatches(ex -> ex instanceof RuntimeException &&
                        ex.getMessage().equals("Kafka broker unavailable"))
                .verify();
    }

    @Test
    void sendPasswordResetTokenMail_emptyToken_stillPublishes() {
        // Arrange — validation is the caller's responsibility; service publishes as-is
        String username = "user@example.com";
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("sentMail", 0), 0L, 0, 0L, 0, 0);
        SendResult<String, NotificationDto> sendResult = mock(SendResult.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(kafkaTemplate.send(eq("sentMail"), any(NotificationDto.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        // Act
        Mono<Void> result = mailService.sendPasswordResetTokenMail(username, "");

        // Assert
        StepVerifier.create(result)
                .verifyComplete();
    }
}
