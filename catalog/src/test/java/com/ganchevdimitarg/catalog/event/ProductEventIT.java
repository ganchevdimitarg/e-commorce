package com.ganchevdimitarg.catalog.event;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import com.ganchevdimitarg.catalog.cache.ProductCache;
import com.ganchevdimitarg.catalog.dto.product.CreateProductCommand;
import com.ganchevdimitarg.catalog.service.product.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test: creates a product through the service and asserts the
 * corresponding event was written to the outbox inside the same transaction.
 *
 * <p>Postgres only — the outbox write never contacts Kafka; delivery is the relay's job
 * (TODO 4.4c). {@link ProductCache} is mocked so the after-commit put does not attempt a
 * Redis connection in this Postgres-only context.
 */
@Tag("integration")
class ProductEventIT extends AbstractIntegrationTest {

    private static final String PRODUCT_NAME = "mouse";
    private static final String CATEGORY_NAME = "peripherals";

    @MockitoBean
    ProductCache productCache;

    @Autowired
    ProductService productService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate txTemplate;

    private UUID productId;

    @BeforeEach
    void setUp() {
        // Seed a category via JDBC so createProduct can resolve it
        jdbc.update("""
                INSERT INTO categories (id, name, created_at, updated_at, version)
                VALUES (?, ?, now(), now(), 0)
                ON CONFLICT (name) WHERE deleted_at IS NULL DO NOTHING
                """, UUID.randomUUID(), CATEGORY_NAME);
    }

    @AfterEach
    void tearDown() {
        if (productId != null) {
            jdbc.update("DELETE FROM outbox_events WHERE aggregate_id = ?", productId.toString());
        }
        // Clean up seeded data to avoid conflicts across test runs
        jdbc.update("DELETE FROM products WHERE name = ?", PRODUCT_NAME);
        jdbc.update("DELETE FROM categories WHERE name = ?", CATEGORY_NAME);
    }

    @Test
    @WithMockUser(authorities = "SCOPE_catalog.write")
    void should_writeOutboxRow_when_productIsCreatedInTransaction() {
        // Given: a product command
        CreateProductCommand cmd = new CreateProductCommand(
                PRODUCT_NAME, "Wireless USB mouse",
                BigDecimal.valueOf(29.99), true, "Bluetooth 5.0",
                CATEGORY_NAME);

        // When: the product is created via the service within a single transaction
        AtomicReference<UUID> createdId = new AtomicReference<>();
        txTemplate.executeWithoutResult(status ->
                createdId.set(productService.createProduct(cmd).id()));
        productId = createdId.get();

        // Then: the outbox row was written with the event payload and headers. jsonb
        // reformats the stored text, so fields are read through the jsonb operators.
        String eventId = jdbc.queryForObject(
                "SELECT payload ->> 'eventId' FROM outbox_events WHERE aggregate_id = ?",
                String.class, productId.toString());
        assertThat(eventId).isNotBlank();
        assertThat(jdbc.queryForObject(
                "SELECT payload ->> 'productName' FROM outbox_events WHERE aggregate_id = ?",
                String.class, productId.toString())).isEqualTo(PRODUCT_NAME);
        assertThat(jdbc.queryForObject(
                "SELECT payload ->> 'occurredAt' FROM outbox_events WHERE aggregate_id = ?",
                String.class, productId.toString())).isNotBlank();
        assertThat(jdbc.queryForObject(
                "SELECT payload ->> 'categoryName' FROM outbox_events WHERE aggregate_id = ?",
                String.class, productId.toString())).isEqualTo(CATEGORY_NAME);
        assertThat(jdbc.queryForObject(
                "SELECT (payload ->> 'price')::numeric FROM outbox_events WHERE aggregate_id = ?",
                BigDecimal.class, productId.toString())).isEqualByComparingTo("29.99");
        assertThat(jdbc.queryForObject(
                "SELECT payload ->> 'inStock' FROM outbox_events WHERE aggregate_id = ?",
                String.class, productId.toString())).isEqualTo("true");
        // A newly inserted product starts at version 0
        assertThat(jdbc.queryForObject(
                "SELECT (payload ->> 'version')::bigint FROM outbox_events WHERE aggregate_id = ?",
                Long.class, productId.toString())).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT (payload ->> 'schemaVersion')::int FROM outbox_events WHERE aggregate_id = ?",
                Integer.class, productId.toString())).isEqualTo(ProductEvent.SCHEMA_VERSION);
        assertThat(jdbc.queryForObject(
                "SELECT sent_at IS NULL FROM outbox_events WHERE aggregate_id = ?",
                Boolean.class, productId.toString())).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT headers ->> 'eventType' FROM outbox_events WHERE aggregate_id = ?",
                String.class, productId.toString())).isEqualTo("created");
        assertThat(jdbc.queryForObject(
                "SELECT headers ->> 'correlationId' FROM outbox_events WHERE aggregate_id = ?",
                String.class, productId.toString())).isEqualTo(eventId);
        assertThat(jdbc.queryForObject(
                "SELECT topic FROM outbox_events WHERE aggregate_id = ?",
                String.class, productId.toString())).isEqualTo(ProductTopics.PRODUCT_CREATED);
        assertThat(jdbc.queryForObject(
                "SELECT message_key FROM outbox_events WHERE aggregate_id = ?",
                String.class, productId.toString())).isEqualTo(productId.toString());
        assertThat(jdbc.queryForObject(
                "SELECT aggregate_type FROM outbox_events WHERE aggregate_id = ?",
                String.class, productId.toString())).isEqualTo("Product");
    }
}
