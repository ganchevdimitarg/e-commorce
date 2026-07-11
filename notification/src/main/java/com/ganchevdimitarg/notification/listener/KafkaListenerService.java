package com.ganchevdimitarg.notification.listener;

import com.ganchevdimitarg.notification.config.KafkaTopics;
import com.ganchevdimitarg.notification.dto.NotificationDto;
import com.ganchevdimitarg.notification.service.EmailService;
import com.ganchevdimitarg.notification.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code order.notification.requested}. A failure (e.g. transient SMTP outage)
 * is redelivered via topic-based non-blocking retry ({@code .retry-0}, {@code .retry-1}, ...)
 * with exponential backoff; once attempts are exhausted the record lands on
 * {@code order.notification.requested.DLT} and is handled by {@link #handleDlt}.
 *
 * <p>Retries are deduplicated by {@link IdempotencyService}'s claim key: a record is only
 * suppressed as a duplicate once an attempt has completed successfully, and a failed attempt
 * releases the claim so the redelivery can reprocess rather than being permanently dropped.
 * Caveat: {@code EmailServiceImpl} sends over SMTP first and persists afterwards, so a failure
 * after the send but before persistence also releases the claim — a retry may then resend an
 * already-delivered email (pre-existing send-then-persist ordering, tracked as a follow-up).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaListenerService {

    private final EmailService emailService;
    private final IdempotencyService idempotencyService;

    @RetryableTopic(
            attempts = "4",
            backOff = @BackOff(delay = 1_000, multiplier = 2.0),
            retryTopicSuffix = ".retry",
            dltTopicSuffix = ".DLT",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(topics = KafkaTopics.ORDER_NOTIFICATION_REQUESTED,
            groupId = KafkaTopics.GROUP, containerFactory = "messageListener")
    public void onEmailRequested(@Payload NotificationDto dto,
                                 @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        String idempotencyKey = key != null ? key : dto.recipient() + ":" + dto.subject();
        idempotencyService.runOnce(idempotencyKey, () -> emailService.sendSimpleMail(dto));
    }

    @DltHandler
    public void handleDlt(@Payload(required = false) NotificationDto dto,
                          @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(KafkaHeaders.OFFSET) long offset,
                          @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String errorMessage) {
        log.error("Notification dead-lettered: key={}, topic={}, offset={}, payload={}, error={}",
                key, topic, offset, dto, errorMessage);
    }
}
