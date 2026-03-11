package com.ganchevdimitarg.profile.mailService;

import com.ganchevdimitarg.profile.dto.NotificationDto;
import com.ganchevdimitarg.profile.service.MailServiceImpl;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import reactor.test.StepVerifier;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {
    private static final String TOPIC = "test-mail-topic";
    public static final String TEST_USERNAME = "user@example.com";

    @Mock
    private KafkaTemplate<String, NotificationDto> kafkaTemplate;

    private MailServiceImpl mailService;

    @BeforeEach
    void setUp() {
        mailService = new MailServiceImpl(kafkaTemplate, TOPIC);
    }

    @Test
    void sendUserWelcomeMail_ShouldPublishToKafkaSuccessfully() {
        var expectedDto = new NotificationDto(
                TEST_USERNAME,
                "Registration",
                "You have successfully registered. Please log in to your account."
        );

        setupMockKafkaResponse();

        StepVerifier.create(mailService.sendUserWelcomeMail(TEST_USERNAME))
                .verifyComplete();

        // ФИКС: Използваме eq() и за двата аргумента
        verify(kafkaTemplate).send(eq(TOPIC), eq(expectedDto));
    }

    @Test
    void sendPasswordResetTokenMail_ShouldPublishToKafkaSuccessfully() {
        var token = "secret-jwt-token";
        var expectedDto = new NotificationDto(TEST_USERNAME, "Password reset token", token);

        setupMockKafkaResponse();

        StepVerifier.create(mailService.sendPasswordResetTokenMail(TEST_USERNAME, token))
                .verifyComplete();

        verify(kafkaTemplate).send(eq(TOPIC), eq(expectedDto));
    }

    @Test
    void sendUserWelcomeMail_ShouldHandleError() {
        // Arrange
        var future = new CompletableFuture<SendResult<String, NotificationDto>>();
        future.completeExceptionally(new RuntimeException("Kafka connection failed"));

        when(kafkaTemplate.send(anyString(), any(NotificationDto.class))).thenReturn(future);

        // Act & Assert
        StepVerifier.create(mailService.sendUserWelcomeMail("error@test.com"))
                .expectError(RuntimeException.class)
                .verify();

        verify(kafkaTemplate).send(eq(TOPIC), any(NotificationDto.class));
    }

    private void setupMockKafkaResponse() {
        var metadata = Mockito.mock(RecordMetadata.class);
        when(metadata.offset()).thenReturn(1L);

        @SuppressWarnings("unchecked")
        SendResult<String, NotificationDto> sendResult = Mockito.mock(SendResult.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);

        var future = CompletableFuture.completedFuture(sendResult);

        Mockito.doReturn(future).when(kafkaTemplate).send(anyString(), any(NotificationDto.class));
    }
}




