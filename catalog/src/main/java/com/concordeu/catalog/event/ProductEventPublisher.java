package com.concordeu.catalog.event;

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

    public void publishCreated(String productName) {
        kafkaTemplate.send(CREATED, productName, new ProductEvent.ProductCreated(productName));
    }

    public void publishUpdated(String productName) {
        kafkaTemplate.send(UPDATED, productName, new ProductEvent.ProductUpdated(productName));
    }

    public void publishDeleted(String productName) {
        kafkaTemplate.send(DELETED, productName, new ProductEvent.ProductDeleted(productName));
    }
}
