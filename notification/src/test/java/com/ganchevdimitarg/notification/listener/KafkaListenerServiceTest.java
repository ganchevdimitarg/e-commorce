package com.ganchevdimitarg.notification.listener;

import com.ganchevdimitarg.notification.dto.NotificationDto;
import com.ganchevdimitarg.notification.service.EmailService;
import com.ganchevdimitarg.notification.service.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class KafkaListenerServiceTest {

    @Mock
    private EmailService emailService;

    @Mock
    private IdempotencyService idempotencyService;

    private final NotificationDto dto =
            new NotificationDto("user@example.com", "Order", "You have successfully created an order.");

    @Test
    void should_runViaIdempotencyGuard_when_messageReceivedWithKey() {
        var listener = new KafkaListenerService(emailService, idempotencyService);
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(1)).run();
            return null;
        }).when(idempotencyService).runOnce(anyString(), any(Runnable.class));

        listener.onEmailRequested(dto, "evt-1");

        verify(idempotencyService).runOnce(eq("evt-1"), any(Runnable.class));
        verify(emailService).sendSimpleMail(dto);
    }

    @Test
    void should_deriveKeyFromRecipientAndSubject_when_headerMissing() {
        var listener = new KafkaListenerService(emailService, idempotencyService);

        listener.onEmailRequested(dto, null);

        verify(idempotencyService).runOnce(eq("user@example.com:Order"), any(Runnable.class));
    }

    @Test
    void should_declareRetryableTopic_when_annotatedForRedelivery() throws NoSuchMethodException {
        Method listenerMethod = KafkaListenerService.class.getMethod(
                "onEmailRequested", NotificationDto.class, String.class);

        var retryableTopic = listenerMethod.getAnnotation(
                org.springframework.kafka.annotation.RetryableTopic.class);

        assertThat(retryableTopic).isNotNull();
        assertThat(retryableTopic.attempts()).isEqualTo("4");
        assertThat(retryableTopic.dltTopicSuffix()).isEqualTo(".DLT");
    }

    @Test
    void should_logAndNotThrow_when_recordDeadLettered() {
        var listener = new KafkaListenerService(emailService, idempotencyService);

        assertThatCode(() -> listener.handleDlt(dto, "evt-1",
                "order.notification.requested.retry-3", 42L, "boom"))
                .doesNotThrowAnyException();

        verifyNoInteractions(emailService, idempotencyService);
    }

    @Test
    void should_logAndNotThrow_when_deadLetteredRecordHasNullPayloadOrKey() {
        var listener = new KafkaListenerService(emailService, idempotencyService);

        assertThatCode(() -> listener.handleDlt(null, null,
                "order.notification.requested.retry-3", 0L, null))
                .doesNotThrowAnyException();
    }
}
