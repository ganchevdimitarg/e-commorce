package com.concordeu.catalog.event;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Tag("unit")
class ProductEventPublisherTest {

    @Test
    void should_sendToCreatedTopic_when_productCreated() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, ProductEvent> template = mock(KafkaTemplate.class);
        ProductEventPublisher publisher = new ProductEventPublisher(template);

        publisher.publishCreated("mouse");

        verify(template).send(eq("catalog.product.created"), eq("mouse"),
                eq(new ProductEvent.ProductCreated("mouse")));
    }
}
