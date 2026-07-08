package com.ganchevdimitarg.payment.event;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Publishes payment domain events to Kafka. Fire-and-forget: the send is async and its
 * outcome never blocks or fails the charge — a broker outage is logged and metered, not
 * propagated. Mirrors catalog's {@code ProductEventPublisher}. Called only after the local
 * charge row has committed, so a published event always reflects durable state.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    static final String CHARGE_COMPLETED_TOPIC = "payment.charge.completed";
    private static final String COMPLETED_EVENT_TYPE = "payment.completed";

    private final KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    /**
     * Publishes a {@link PaymentCompletedEvent}. Keyed by {@code orderId} so all events for
     * one order share a partition and stay ordered.
     */
    public void publishCompleted(PaymentCompletedEvent event) {
        ProducerRecord<String, PaymentCompletedEvent> record =
                new ProducerRecord<>(CHARGE_COMPLETED_TOPIC, event.orderId(), event);
        record.headers().add("eventType", COMPLETED_EVENT_TYPE.getBytes(StandardCharsets.UTF_8));
        record.headers().add("correlationId", event.eventId().getBytes(StandardCharsets.UTF_8));
        record.headers().add("traceId", traceId().getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish {} for order {}: {}",
                        CHARGE_COMPLETED_TOPIC, event.orderId(), ex.getMessage(), ex);
                meterRegistry.counter("payment.event.send.failed", "topic", CHARGE_COMPLETED_TOPIC).increment();
            } else {
                meterRegistry.counter("payment.charge.completed").increment();
            }
        });
    }

    private static String traceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : "";
    }
}
