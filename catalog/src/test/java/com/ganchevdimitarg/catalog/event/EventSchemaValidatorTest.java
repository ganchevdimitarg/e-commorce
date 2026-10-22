package com.ganchevdimitarg.catalog.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the producer-side JSON Schema contract described by ADR-010.
 *
 * <p>The schemas are loaded from the real classpath resources, not stubs, so a drift between
 * {@link ProductEvent} and its schema fails here. The event fixtures use real UUIDs and ISO
 * instants because that is what the writer emits.
 */
@Tag("unit")
class EventSchemaValidatorTest {

    private static final String EVENT_ID = "4f6c1c1e-2b87-4c58-9f0e-2a1b6d8e5c31";
    private static final String PRODUCT_ID = "9a7d4f22-1c3e-4b6f-8d2a-7e5c0b1f3a49";
    private static final String PRODUCT_NAME = "wireless mouse";
    private static final BigDecimal PRICE = BigDecimal.valueOf(29.99);
    private static final String CATEGORY_NAME = "peripherals";
    private static final long VERSION = 3L;
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-21T12:00:00Z");

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final EventSchemaValidator validator = new EventSchemaValidator(MAPPER);

    private static ProductEvent.ProductCreated createdEvent() {
        return new ProductEvent.ProductCreated(EVENT_ID, PRODUCT_ID, PRODUCT_NAME, PRICE, true,
                CATEGORY_NAME, VERSION, ProductEvent.SCHEMA_VERSION, OCCURRED_AT);
    }

    private static ProductEvent.ProductUpdated updatedEvent() {
        return new ProductEvent.ProductUpdated(EVENT_ID, PRODUCT_ID, PRODUCT_NAME, PRICE, true,
                CATEGORY_NAME, VERSION, ProductEvent.SCHEMA_VERSION, OCCURRED_AT);
    }

    private static ProductEvent.ProductDeleted deletedEvent() {
        return new ProductEvent.ProductDeleted(EVENT_ID, PRODUCT_ID, PRODUCT_NAME, PRICE, false,
                CATEGORY_NAME, VERSION, ProductEvent.SCHEMA_VERSION, OCCURRED_AT);
    }

    private static ProductEvent.ProductCategoryChanged categoryChangedEvent(String previousCategoryName) {
        return new ProductEvent.ProductCategoryChanged(EVENT_ID, PRODUCT_ID, PRODUCT_NAME, PRICE, true,
                CATEGORY_NAME, previousCategoryName, VERSION, ProductEvent.SCHEMA_VERSION, OCCURRED_AT);
    }

    private static Map<String, Object> payload(ProductEvent event) {
        return MAPPER.convertValue(event, new TypeReference<Map<String, Object>>() { });
    }

    @Test
    void should_acceptEveryProductEvent_when_payloadMatchesItsSchema() {
        assertThatCode(() -> validator.validate(ProductTopics.PRODUCT_CREATED, createdEvent()))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(ProductTopics.PRODUCT_UPDATED, updatedEvent()))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(ProductTopics.PRODUCT_DELETED, deletedEvent()))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(ProductTopics.PRODUCT_CATEGORY_CHANGED,
                categoryChangedEvent("storage")))
                .doesNotThrowAnyException();
    }

    @Test
    void should_acceptEvents_when_optionalCategoryFieldsAreNull() {
        ProductEvent.ProductCreated withoutCategory = new ProductEvent.ProductCreated(EVENT_ID,
                PRODUCT_ID, PRODUCT_NAME, PRICE, true, null, VERSION, ProductEvent.SCHEMA_VERSION,
                OCCURRED_AT);
        ProductEvent.ProductCategoryChanged withoutPreviousCategory = categoryChangedEvent(null);

        assertThatCode(() -> validator.validate(ProductTopics.PRODUCT_CREATED, withoutCategory))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(ProductTopics.PRODUCT_CATEGORY_CHANGED,
                withoutPreviousCategory))
                .doesNotThrowAnyException();
    }

    @Test
    void should_rejectEvent_when_requiredFieldIsMissing() {
        Map<String, Object> payload = payload(createdEvent());
        payload.remove("productName");

        assertViolation(ProductTopics.PRODUCT_CREATED, payload, "productName");
    }

    @Test
    void should_rejectEvent_when_fieldHasTheWrongType() {
        Map<String, Object> payload = payload(createdEvent());
        payload.put("price", "29.99");

        assertViolation(ProductTopics.PRODUCT_CREATED, payload, "price");
    }

    @Test
    void should_rejectEvent_when_unknownFieldIsPresent() {
        Map<String, Object> payload = payload(createdEvent());
        payload.put("discount", 5);

        assertViolation(ProductTopics.PRODUCT_CREATED, payload, "discount");
    }

    @Test
    void should_rejectEvent_when_schemaVersionHasNoSchema() {
        Map<String, Object> payload = payload(createdEvent());
        payload.put("schemaVersion", 99);

        assertThatThrownBy(() -> validator.validate(ProductTopics.PRODUCT_CREATED, payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("version 99");
    }

    @Test
    void should_validateAgainstMatchingVersion_when_eventCarriesSchemaVersionTwo() {
        EventSchemaCatalog catalog = new EventSchemaCatalog(MAPPER, "event-schemas-fixture",
                List.of(ProductTopics.PRODUCT_CREATED));
        EventSchemaValidator versioned = new EventSchemaValidator(MAPPER, catalog);
        ProductEvent.ProductCreated versionTwo = new ProductEvent.ProductCreated(EVENT_ID,
                PRODUCT_ID, PRODUCT_NAME, PRICE, true, CATEGORY_NAME, VERSION, 2, OCCURRED_AT);

        assertThatCode(() -> versioned.validate(ProductTopics.PRODUCT_CREATED, versionTwo))
                .doesNotThrowAnyException();
    }

    @Test
    void should_rejectCategoryChangedEvent_when_previousCategoryNameIsMissing() {
        Map<String, Object> payload = payload(categoryChangedEvent("storage"));
        payload.remove("previousCategoryName");

        assertViolation(ProductTopics.PRODUCT_CATEGORY_CHANGED, payload, "previousCategoryName");
    }

    @Test
    void should_rejectEvent_when_topicHasNoSchema() {
        assertThatThrownBy(() -> validator.validate("catalog.product.unknown", createdEvent()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("catalog.product.unknown");
    }

    @Test
    void should_failToLoad_when_aSchemaResourceIsMissing() {
        assertThatThrownBy(() -> new EventSchemaCatalog(MAPPER, "missing-event-schemas",
                ProductTopics.ALL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing-event-schemas");
    }

    @Test
    void should_loadSchemaForEveryProductTopic() {
        EventSchemaCatalog catalog = new EventSchemaCatalog(MAPPER, "event-schemas", ProductTopics.ALL);

        assertThat(ProductTopics.ALL).allSatisfy(topic ->
                assertThat(catalog.versionsFor(topic)).containsExactly(1));
    }

    private void assertViolation(String topic, Object payload, String offendingField) {
        assertThatThrownBy(() -> validator.validate(topic, payload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(topic)
                .hasMessageContaining(offendingField);
    }
}
