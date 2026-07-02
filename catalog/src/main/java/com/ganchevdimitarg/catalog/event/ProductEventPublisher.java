package com.ganchevdimitarg.catalog.event;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventPublisher {

    private static final String CREATED = "catalog.product.created";
    private static final String UPDATED = "catalog.product.updated";
    private static final String DELETED = "catalog.product.deleted";

    private final KafkaTemplate<String, ProductEvent> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public void publishCreated(String productId, String productName) {
        ProductEvent event = new ProductEvent.ProductCreated(newEventId(), productId, productName, Instant.now());
        send(CREATED, "created", productId, event);
    }

    public void publishUpdated(String productId, String productName) {
        ProductEvent event = new ProductEvent.ProductUpdated(newEventId(), productId, productName, Instant.now());
        send(UPDATED, "updated", productId, event);
    }

    public void publishDeleted(String productId, String productName) {
        ProductEvent event = new ProductEvent.ProductDeleted(newEventId(), productId, productName, Instant.now());
        send(DELETED, "deleted", productId, event);
    }

    private void send(String topic, String eventType, String key, ProductEvent event) {
        ProducerRecord<String, ProductEvent> record = new ProducerRecord<>(topic, key, event);
        record.headers().add("eventType", eventType.getBytes(StandardCharsets.UTF_8));
        record.headers().add("correlationId", event.eventId().getBytes(StandardCharsets.UTF_8));
        record.headers().add("traceId", traceId().getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send event to {}: {}", topic, ex.getMessage(), ex);
                meterRegistry.counter("catalog.event.send.failed", "topic", topic).increment();
            }
        });
    }

    private static String newEventId() {
        return UUID.randomUUID().toString();
    }

    private static String traceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : "";
    }
}
