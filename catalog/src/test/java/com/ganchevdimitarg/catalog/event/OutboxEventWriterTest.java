package com.ganchevdimitarg.catalog.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ganchevdimitarg.catalog.domain.OutboxEvent;
import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.repository.OutboxEventRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OutboxEventWriterTest {

    private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PRODUCT_NAME = "mouse";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Mock
    OutboxEventRepository repository;

    private OutboxEventWriter newWriter() {
        return new OutboxEventWriter(repository, MAPPER);
    }

    private OutboxEvent captureSaved() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private static Product product() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName(PRODUCT_NAME);
        return product;
    }

    @Test
    void should_writeCreatedRowWithTopicPayloadAndHeaders() {
        newWriter().writeCreated(product());

        OutboxEvent row = captureSaved();
        assertThat(row.getTopic()).isEqualTo(ProductTopics.PRODUCT_CREATED);
        assertThat(row.getEventType()).isEqualTo("created");
        assertThat(row.getAggregateType()).isEqualTo("Product");
        assertThat(row.getPayload()).contains("\"productName\":\"mouse\"");
        assertThat(row.getHeaders())
                .containsEntry("eventType", "created")
                .containsKey("correlationId")
                .containsKey("traceId");
    }

    @Test
    void should_useProductIdAsMessageKey_andAggregateId() {
        newWriter().writeCreated(product());

        OutboxEvent row = captureSaved();
        assertThat(row.getMessageKey()).isEqualTo(PRODUCT_ID.toString());
        assertThat(row.getAggregateId()).isEqualTo(PRODUCT_ID.toString());
    }

    @Test
    void should_setCorrelationIdHeaderToEventId() throws Exception {
        newWriter().writeCreated(product());

        OutboxEvent row = captureSaved();
        JsonNode payload = MAPPER.readTree(row.getPayload());
        assertThat(row.getHeaders().get("correlationId")).isEqualTo(payload.get("eventId").asText());
    }

    @Test
    void should_propagateTraceIdFromMdc() {
        try {
            MDC.put("traceId", "trace-abc");
            newWriter().writeCreated(product());
        } finally {
            MDC.clear();
        }

        assertThat(captureSaved().getHeaders()).containsEntry("traceId", "trace-abc");
    }

    @Test
    void should_leaveSentAtNull_andAttemptsZero() {
        newWriter().writeCreated(product());

        OutboxEvent row = captureSaved();
        assertThat(row.getSentAt()).isNull();
        assertThat(row.getLastError()).isNull();
        assertThat(row.getAttempts()).isZero();
        assertThat(row.getCreatedAt()).isNotNull();
        assertThat(row.getNextAttemptAt()).isNotNull();
    }

    @Test
    void should_serialiseOccurredAtAsIsoString() throws Exception {
        newWriter().writeCreated(product());

        JsonNode payload = MAPPER.readTree(captureSaved().getPayload());
        String occurredAt = payload.get("occurredAt").asText();

        assertThat(occurredAt).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
        assertThat(Instant.parse(occurredAt)).isNotNull();
    }
}
