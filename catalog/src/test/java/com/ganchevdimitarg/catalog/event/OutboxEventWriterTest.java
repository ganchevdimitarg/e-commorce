package com.ganchevdimitarg.catalog.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ganchevdimitarg.catalog.domain.Category;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OutboxEventWriterTest {

    private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PRODUCT_NAME = "mouse";
    private static final String CATEGORY_NAME = "peripherals";
    private static final BigDecimal PRICE = BigDecimal.valueOf(19.99);
    private static final long VERSION = 7L;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** The real validator, so every fixture here also proves the payload matches its schema. */
    private static final EventSchemaValidator VALIDATOR = new EventSchemaValidator(MAPPER);

    @Mock
    OutboxEventRepository repository;

    private OutboxEventWriter newWriter() {
        return new OutboxEventWriter(repository, MAPPER, VALIDATOR);
    }

    private OutboxEvent captureSaved() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private static Product product() {
        Category category = new Category();
        category.setName(CATEGORY_NAME);

        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName(PRODUCT_NAME);
        product.setPrice(PRICE);
        product.setInStock(true);
        product.setVersion(VERSION);
        product.setCategory(category);
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
    void should_notWriteRow_when_schemaValidationFails() {
        EventSchemaValidator validator = mock(EventSchemaValidator.class);
        doThrow(new IllegalStateException("Event violates its JSON Schema"))
                .when(validator).validate(eq(ProductTopics.PRODUCT_CREATED), any());
        OutboxEventWriter writer = new OutboxEventWriter(repository, MAPPER, validator);

        assertThatThrownBy(() -> writer.writeCreated(product()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("violates");

        verifyNoInteractions(repository);
    }

    @Test
    void should_validateAgainstTheTopicSchema_when_writingAnEvent() {
        EventSchemaValidator validator = mock(EventSchemaValidator.class);
        OutboxEventWriter writer = new OutboxEventWriter(repository, MAPPER, validator);

        writer.writeCategoryChanged(product(), "storage", CATEGORY_NAME);

        verify(validator).validate(eq(ProductTopics.PRODUCT_CATEGORY_CHANGED), any(ProductEvent.class));
        verify(repository).save(any(OutboxEvent.class));
    }

    @Test
    void should_serialiseEnrichedVersionedPayload_when_productIsWritten() throws Exception {
        newWriter().writeCreated(product());

        String payload = captureSaved().getPayload();
        assertThat(payload).contains("\"schemaVersion\":1");
        JsonNode node = MAPPER.readTree(payload);
        assertThat(node.get("price").decimalValue()).isEqualByComparingTo(PRICE);
        assertThat(node.get("inStock").asBoolean()).isTrue();
        assertThat(node.get("categoryName").asText()).isEqualTo(CATEGORY_NAME);
        assertThat(node.get("version").asLong()).isEqualTo(VERSION);
        assertThat(node.get("schemaVersion").asInt()).isEqualTo(ProductEvent.SCHEMA_VERSION);
    }

    @Test
    void should_writeCategoryChangedRowWithPreviousCategoryAndNextVersion() throws Exception {
        newWriter().writeCategoryChanged(product(), "storage", CATEGORY_NAME);

        OutboxEvent row = captureSaved();
        assertThat(row.getTopic()).isEqualTo(ProductTopics.PRODUCT_CATEGORY_CHANGED);
        assertThat(row.getEventType()).isEqualTo("category-changed");
        assertThat(row.getHeaders()).containsEntry("eventType", "category-changed");

        JsonNode payload = MAPPER.readTree(row.getPayload());
        assertThat(payload.get("previousCategoryName").asText()).isEqualTo("storage");
        assertThat(payload.get("categoryName").asText()).isEqualTo(CATEGORY_NAME);
        // The bulk update increments the row's version by one, so the event carries it
        assertThat(payload.get("version").asLong()).isEqualTo(VERSION + 1);
        assertThat(payload.get("schemaVersion").asInt()).isEqualTo(ProductEvent.SCHEMA_VERSION);
    }

    @Test
    void should_writeUpdatedRowWithUpdatedTopicAndEventType() throws Exception {
        newWriter().writeUpdated(product());

        OutboxEvent row = captureSaved();
        assertThat(row.getTopic()).isEqualTo(ProductTopics.PRODUCT_UPDATED);
        assertThat(row.getEventType()).isEqualTo("updated");
        assertThat(row.getHeaders()).containsEntry("eventType", "updated");
        assertEnrichedPayload(MAPPER.readTree(row.getPayload()));
    }

    @Test
    void should_writeDeletedRowWithDeletedTopicAndEventType() throws Exception {
        newWriter().writeDeleted(product());

        OutboxEvent row = captureSaved();
        assertThat(row.getTopic()).isEqualTo(ProductTopics.PRODUCT_DELETED);
        assertThat(row.getEventType()).isEqualTo("deleted");
        assertThat(row.getHeaders()).containsEntry("eventType", "deleted");
        assertEnrichedPayload(MAPPER.readTree(row.getPayload()));
    }

    private static void assertEnrichedPayload(JsonNode payload) {
        assertThat(payload.get("price").decimalValue()).isEqualByComparingTo(PRICE);
        assertThat(payload.get("inStock").asBoolean()).isTrue();
        assertThat(payload.get("categoryName").asText()).isEqualTo(CATEGORY_NAME);
        assertThat(payload.get("version").asLong()).isEqualTo(VERSION);
        assertThat(payload.get("schemaVersion").asInt()).isEqualTo(ProductEvent.SCHEMA_VERSION);
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
