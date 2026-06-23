package com.concordeu.catalog.event;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventPublisher {

    private static final String CREATED = "catalog.product.created";
    private static final String UPDATED = "catalog.product.updated";
    private static final String DELETED = "catalog.product.deleted";

    private final KafkaTemplate<String, ProductEvent> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public void publishCreated(String productName) {
        send(CREATED, productName, new ProductEvent.ProductCreated(productName));
    }

    public void publishUpdated(String productName) {
        send(UPDATED, productName, new ProductEvent.ProductUpdated(productName));
    }

    public void publishDeleted(String productName) {
        send(DELETED, productName, new ProductEvent.ProductDeleted(productName));
    }

    private void send(String topic, String key, ProductEvent event) {
        kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send event to {}: {}", topic, ex.getMessage(), ex);
                        meterRegistry.counter("catalog.event.send.failed", "topic", topic).increment();
                    }
                });
    }
}
