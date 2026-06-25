package com.concordeu.catalog.event;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
class ProductEventPublisherTest {

    @SuppressWarnings("unchecked") // Mockito generic erasure on mock(KafkaTemplate.class)
    private final KafkaTemplate<String, ProductEvent> template = mock(KafkaTemplate.class);

    @Test
    void should_sendRecordWithTraceAndCorrelationHeaders() {
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        ProductEventPublisher publisher = new ProductEventPublisher(template, new SimpleMeterRegistry());

        publisher.publishCreated("p1", "mouse");

        ArgumentCaptor<ProducerRecord<String, ProductEvent>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        ProducerRecord<String, ProductEvent> record = captor.getValue();

        assertThat(record.topic()).isEqualTo("catalog.product.created");
        assertThat(record.key()).isEqualTo("p1");
        assertThat(record.value().productName()).isEqualTo("mouse");
        assertThat(header(record, "eventType")).isEqualTo("created");
        assertThat(header(record, "correlationId")).isEqualTo(record.value().eventId());
        assertThat(record.headers().lastHeader("traceId")).isNotNull();
    }

    @Test
    void should_propagateTraceId_fromMdc() {
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        ProductEventPublisher publisher = new ProductEventPublisher(template, new SimpleMeterRegistry());

        try {
            MDC.put("traceId", "trace-abc");
            publisher.publishCreated("p1", "mouse");
        } finally {
            MDC.clear();
        }

        ArgumentCaptor<ProducerRecord<String, ProductEvent>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        ProducerRecord<String, ProductEvent> record = captor.getValue();

        assertThat(header(record, "traceId")).isEqualTo("trace-abc");
    }

    @Test
    void should_sendUpdatedEvent_onTopicAndType() {
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        ProductEventPublisher publisher = new ProductEventPublisher(template, new SimpleMeterRegistry());

        publisher.publishUpdated("p2", "keyboard");

        ArgumentCaptor<ProducerRecord<String, ProductEvent>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        ProducerRecord<String, ProductEvent> record = captor.getValue();

        assertThat(record.topic()).isEqualTo("catalog.product.updated");
        assertThat(record.key()).isEqualTo("p2");
        assertThat(record.value()).isInstanceOf(ProductEvent.ProductUpdated.class);
        assertThat(header(record, "eventType")).isEqualTo("updated");
    }

    @Test
    void should_sendDeletedEvent_onTopicAndType() {
        when(template.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));
        ProductEventPublisher publisher = new ProductEventPublisher(template, new SimpleMeterRegistry());

        publisher.publishDeleted("p3", "mouse");

        ArgumentCaptor<ProducerRecord<String, ProductEvent>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(template).send(captor.capture());
        ProducerRecord<String, ProductEvent> record = captor.getValue();

        assertThat(record.topic()).isEqualTo("catalog.product.deleted");
        assertThat(record.key()).isEqualTo("p3");
        assertThat(record.value()).isInstanceOf(ProductEvent.ProductDeleted.class);
        assertThat(header(record, "eventType")).isEqualTo("deleted");
    }

    private static String header(ProducerRecord<?, ?> record, String key) {
        return new String(record.headers().lastHeader(key).value(), StandardCharsets.UTF_8);
    }
}
