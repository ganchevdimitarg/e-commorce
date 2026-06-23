package com.concordeu.catalog.event;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class ProductEventPublisherTest {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, ProductEvent> template = mock(KafkaTemplate.class);
    private final ProductEventPublisher publisher = new ProductEventPublisher(template, new SimpleMeterRegistry());

    @Test
    void should_sendToCreatedTopic_when_productCreated() {
        when(template.send(any(String.class), any(String.class), any(ProductEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishCreated("mouse");

        verify(template).send(eq("catalog.product.created"), eq("mouse"),
                eq(new ProductEvent.ProductCreated("mouse")));
    }
}
