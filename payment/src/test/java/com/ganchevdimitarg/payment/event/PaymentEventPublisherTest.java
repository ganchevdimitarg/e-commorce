package com.ganchevdimitarg.payment.event;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentEventPublisherTest {

    private PaymentCompletedEvent event() {
        return new PaymentCompletedEvent("evt-1", "order-1", "ch_1", "cus_1", 500L, "usd", "succeeded", Instant.now());
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_sendToChargeCompletedTopicKeyedByOrder_withHeadersAndSuccessMeter() {
        KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate = mock(KafkaTemplate.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        new PaymentEventPublisher(kafkaTemplate, meterRegistry).publishCompleted(event());

        ArgumentCaptor<ProducerRecord<String, PaymentCompletedEvent>> captor =
                ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, PaymentCompletedEvent> record = captor.getValue();

        assertThat(record.topic()).isEqualTo(PaymentEventPublisher.CHARGE_COMPLETED_TOPIC);
        assertThat(record.key()).isEqualTo("order-1");
        assertThat(header(record, "eventType")).isEqualTo("payment.completed");
        assertThat(header(record, "correlationId")).isEqualTo("evt-1");
        assertThat(record.headers().lastHeader("traceId")).isNotNull();
        assertThat(meterRegistry.counter("payment.charge.completed").count()).isEqualTo(1.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_meterFailureAndNotThrow_when_sendFails() {
        KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate = mock(KafkaTemplate.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        PaymentEventPublisher publisher = new PaymentEventPublisher(kafkaTemplate, meterRegistry);

        // Fire-and-forget: a broker failure is metered, never propagated to the caller.
        assertThatCode(() -> publisher.publishCompleted(event())).doesNotThrowAnyException();
        assertThat(meterRegistry.counter("payment.event.send.failed",
                "topic", PaymentEventPublisher.CHARGE_COMPLETED_TOPIC).count()).isEqualTo(1.0);
    }

    private static String header(ProducerRecord<String, PaymentCompletedEvent> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }
}
