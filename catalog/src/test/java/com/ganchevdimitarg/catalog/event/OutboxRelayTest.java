package com.ganchevdimitarg.catalog.event;

import com.ganchevdimitarg.catalog.domain.OutboxEvent;
import com.ganchevdimitarg.catalog.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.InvalidRecordException;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final UUID EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String PRODUCT_ID = "33333333-3333-3333-3333-333333333333";
    private static final String PAYLOAD =
            "{\"eventId\":\"44444444-4444-4444-4444-444444444444\",\"productName\":\"mouse\"}";

    @Mock
    OutboxEventRepository repository;

    @Mock
    KafkaTemplate<String, String> kafkaTemplate;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private OutboxRelay newRelay() {
        return new OutboxRelay(repository, kafkaTemplate,
                new OutboxProperties(true, 20, Duration.ofSeconds(5)), meterRegistry);
    }

    private static OutboxEvent event() {
        OutboxEvent event = new OutboxEvent();
        event.setId(EVENT_ID);
        event.setTopic(ProductTopics.PRODUCT_CREATED);
        event.setMessageKey(PRODUCT_ID);
        event.setPayload(PAYLOAD);
        event.setHeaders(Map.of("eventType", "created", "correlationId", "e1"));
        return event;
    }

    private static String header(ProducerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    @Test
    void should_markRowSent_when_sendSucceeds() {
        OutboxEvent event = event();
        when(repository.lockUnsentBatch(20)).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        int sent = newRelay().relayOnce();

        assertThat(sent).isEqualTo(1);
        assertThat(event.getSentAt()).isNotNull();
        assertThat(meterRegistry.counter(OutboxRelay.PUBLISHED_METRIC).count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter(OutboxRelay.FAILED_METRIC).count()).isZero();

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.captor();
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, String> record = captor.getValue();
        assertThat(record.topic()).isEqualTo(ProductTopics.PRODUCT_CREATED);
        assertThat(record.key()).isEqualTo(PRODUCT_ID);
        assertThat(record.value()).isEqualTo(PAYLOAD);
        assertThat(header(record, "eventType")).isEqualTo("created");
        assertThat(header(record, "correlationId")).isEqualTo("e1");
    }

    @Test
    void should_returnZero_when_noUnsentRows() {
        when(repository.lockUnsentBatch(20)).thenReturn(List.of());

        assertThat(newRelay().relayOnce()).isZero();
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void should_leaveRowUnsent_when_sendFails() {
        OutboxEvent event = event();
        when(repository.lockUnsentBatch(20)).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenThrow(new KafkaException("broker down"));

        assertThat(newRelay().relayOnce()).isZero();
        assertThat(event.getSentAt()).isNull();
    }

    @Test
    void should_stopBatch_when_sendFails() {
        OutboxEvent first = event();
        OutboxEvent second = event();
        when(repository.lockUnsentBatch(20)).thenReturn(List.of(first, second));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenThrow(new KafkaException("broker down"));

        assertThat(newRelay().relayOnce()).isZero();
        assertThat(first.getSentAt()).isNull();
        assertThat(second.getSentAt()).isNull();
        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    void should_leaveRowDueWithoutAttempt_when_relayIsInterrupted() {
        OutboxEvent event = event();
        when(repository.lockUnsentBatch(20)).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(new InterruptingFuture());

        try {
            assertThat(newRelay().relayOnce()).isZero();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // clear the flag so the test runner is unaffected
        }

        assertThat(event.getSentAt()).isNull();
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getLastError()).isNull();
        assertThat(event.getDeadLetteredAt()).isNull();
        assertThat(meterRegistry.counter(OutboxRelay.FAILED_METRIC).count()).isZero();
        assertThat(meterRegistry.counter(OutboxRelay.DEAD_LETTER_METRIC).count()).isZero();
    }

    @Test
    void should_deadLetter_when_serializationFails() {
        OutboxEvent event = event();
        Instant due = Instant.now().minusSeconds(30);
        event.setNextAttemptAt(due);
        when(repository.lockUnsentBatch(20)).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenThrow(new SerializationException("payload cannot be serialised"));

        assertThat(newRelay().relayOnce()).isZero();

        assertThat(event.getDeadLetteredAt()).isNotNull();
        assertThat(event.getDeadLetterReason()).contains("payload cannot be serialised");
        assertThat(event.getSentAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("payload cannot be serialised");
        // The poison branch parks the row instead of scheduling another attempt.
        assertThat(event.getNextAttemptAt()).isEqualTo(due);
        assertThat(meterRegistry.counter(OutboxRelay.DEAD_LETTER_METRIC).count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter(OutboxRelay.FAILED_METRIC).count()).isZero();
    }

    @Test
    void should_notDeadLetter_when_brokerTimesOut() {
        OutboxEvent event = event();
        when(repository.lockUnsentBatch(20)).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenThrow(new TimeoutException("broker timed out"));
        Instant before = Instant.now();

        assertThat(newRelay().relayOnce()).isZero();

        assertThat(event.getDeadLetteredAt()).isNull();
        assertThat(event.getDeadLetterReason()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt())
                .isBetween(before.plusSeconds(1), Instant.now().plusSeconds(5));
        assertThat(meterRegistry.counter(OutboxRelay.FAILED_METRIC).count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter(OutboxRelay.DEAD_LETTER_METRIC).count()).isZero();
    }

    @Test
    void should_detectWrappedPoison_when_causeIsSerialization() {
        assertThat(OutboxRelay.isPoison(
                new KafkaException(new SerializationException("bad payload")))).isTrue();
    }

    @Test
    void should_detectPoison_when_errorIsRecordTooLarge() {
        assertThat(OutboxRelay.isPoison(new RecordTooLargeException("record too large"))).isTrue();
    }

    @Test
    void should_detectPoison_when_errorIsInvalidRecord() {
        assertThat(OutboxRelay.isPoison(new InvalidRecordException("invalid record"))).isTrue();
    }

    @Test
    void should_detectPoison_when_errorIsIllegalArgument() {
        assertThat(OutboxRelay.isPoison(new IllegalArgumentException("bad argument"))).isTrue();
    }

    @Test
    void should_notDetectPoison_when_errorIsRetryable() {
        assertThat(OutboxRelay.isPoison(new KafkaException("broker down"))).isFalse();
    }

    @Test
    void should_countOnlyLiveRows_when_updatingPendingGauge() {
        when(repository.lockUnsentBatch(20)).thenReturn(List.of());
        when(repository.countBySentAtIsNullAndDeadLetteredAtIsNull()).thenReturn(3L);

        newRelay().relayOnce();

        assertThat(meterRegistry.get(OutboxRelay.PENDING_METRIC).gauge().value()).isEqualTo(3.0);
    }

    @Test
    void should_setBackoffAndLastError_when_sendFails() {
        OutboxEvent event = event();
        event.setAttempts(0);
        when(repository.lockUnsentBatch(20)).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenThrow(new KafkaException("broker down"));
        Instant before = Instant.now();

        assertThat(newRelay().relayOnce()).isZero();

        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("broker down");
        assertThat(event.getNextAttemptAt())
                .isBetween(before.plusSeconds(1), Instant.now().plusSeconds(5));
        assertThat(meterRegistry.counter(OutboxRelay.FAILED_METRIC).count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter(OutboxRelay.PUBLISHED_METRIC).count()).isZero();
    }

    @Test
    void should_capBackoffAt60Seconds_when_attemptsHigh() {
        assertThat(OutboxRelay.backoff(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(OutboxRelay.backoff(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(OutboxRelay.backoff(6)).isEqualTo(Duration.ofSeconds(32));
        assertThat(OutboxRelay.backoff(7)).isEqualTo(Duration.ofSeconds(60));
        assertThat(OutboxRelay.backoff(10)).isEqualTo(Duration.ofSeconds(60));
        assertThat(OutboxRelay.backoff(100)).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void should_fallBackToExceptionType_when_messageIsNull() {
        OutboxEvent event = event();
        when(repository.lockUnsentBatch(20)).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenThrow(new KafkaException((String) null));

        assertThat(newRelay().relayOnce()).isZero();

        assertThat(event.getLastError()).isEqualTo("KafkaException");
    }

    @Test
    void should_truncateLastError_when_messageExceedsLimit() {
        OutboxEvent event = event();
        when(repository.lockUnsentBatch(20)).thenReturn(List.of(event));
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenThrow(new KafkaException("x".repeat(2000)));

        assertThat(newRelay().relayOnce()).isZero();

        assertThat(event.getLastError()).hasSize(1000);
    }

    /** A send future that reports an interrupt, as the relay's blocking get() would. */
    private static final class InterruptingFuture extends CompletableFuture<SendResult<String, String>> {

        @Override
        public SendResult<String, String> get(long timeout, TimeUnit unit) throws InterruptedException {
            throw new InterruptedException("stop requested");
        }
    }
}
