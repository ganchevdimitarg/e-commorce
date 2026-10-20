package com.ganchevdimitarg.catalog.event;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class ProductEventTest {

    private static final String EVENT_ID = "e1";
    private static final String PRODUCT_ID = "p1";
    private static final String PRODUCT_NAME = "mouse";
    private static final BigDecimal PRICE = BigDecimal.valueOf(19.99);
    private static final String CATEGORY_NAME = "peripherals";
    private static final long VERSION = 3L;
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-20T10:15:30Z");

    @Test
    void should_exposeSchemaVersionConstant() {
        assertThat(ProductEvent.SCHEMA_VERSION).isEqualTo(1);
    }

    @Test
    void should_exposeEnrichedCommonFields_onEverySubtype() {
        List<ProductEvent> events = List.of(
                new ProductEvent.ProductCreated(EVENT_ID, PRODUCT_ID, PRODUCT_NAME, PRICE, true,
                        CATEGORY_NAME, VERSION, ProductEvent.SCHEMA_VERSION, OCCURRED_AT),
                new ProductEvent.ProductUpdated(EVENT_ID, PRODUCT_ID, PRODUCT_NAME, PRICE, true,
                        CATEGORY_NAME, VERSION, ProductEvent.SCHEMA_VERSION, OCCURRED_AT),
                new ProductEvent.ProductDeleted(EVENT_ID, PRODUCT_ID, PRODUCT_NAME, PRICE, false,
                        CATEGORY_NAME, VERSION, ProductEvent.SCHEMA_VERSION, OCCURRED_AT),
                new ProductEvent.ProductCategoryChanged(EVENT_ID, PRODUCT_ID, PRODUCT_NAME, PRICE, true,
                        CATEGORY_NAME, "storage", VERSION, ProductEvent.SCHEMA_VERSION, OCCURRED_AT));

        assertThat(events).hasSize(4).allSatisfy(event -> {
            assertThat(event.eventId()).isEqualTo(EVENT_ID);
            assertThat(event.productId()).isEqualTo(PRODUCT_ID);
            assertThat(event.productName()).isEqualTo(PRODUCT_NAME);
            assertThat(event.price()).isEqualByComparingTo(PRICE);
            assertThat(event.categoryName()).isEqualTo(CATEGORY_NAME);
            assertThat(event.version()).isEqualTo(VERSION);
            assertThat(event.schemaVersion()).isEqualTo(1);
            assertThat(event.occurredAt()).isEqualTo(OCCURRED_AT);
        });
        assertThat(events.get(0).inStock()).isTrue();
        assertThat(events.get(2).inStock()).isFalse();
    }

    @Test
    void should_exposePreviousCategoryName_onCategoryChangedEvent() {
        ProductEvent.ProductCategoryChanged event = new ProductEvent.ProductCategoryChanged(
                EVENT_ID, PRODUCT_ID, PRODUCT_NAME, PRICE, true, CATEGORY_NAME, "storage",
                VERSION, ProductEvent.SCHEMA_VERSION, OCCURRED_AT);

        assertThat(event.previousCategoryName()).isEqualTo("storage");
        assertThat(event.categoryName()).isEqualTo(CATEGORY_NAME);
    }
}
