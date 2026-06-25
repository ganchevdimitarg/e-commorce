package com.concordeu.catalog.event;

import com.concordeu.catalog.RedisKafkaIntegrationBase;
import com.concordeu.catalog.dto.product.CreateProductCommand;
import com.concordeu.catalog.service.product.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end integration test: creates a product via the service layer and asserts
 * the corresponding {@code catalog.product.created} Kafka event arrives on the topic
 * with the correct payload fields and message headers.
 */
@Tag("integration")
class ProductEventIT extends RedisKafkaIntegrationBase {

    private static final String TOPIC = "catalog.product.created";
    private static final String PRODUCT_NAME = "mouse";
    private static final String CATEGORY_NAME = "peripherals";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Autowired
    private ProductService productService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate txTemplate;

    private KafkaConsumer<String, byte[]> consumer;

    @BeforeEach
    void setUp() {
        // Seed a category via JDBC so createProduct can resolve it
        UUID categoryId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO categories (id, name, created_at, updated_at, version)
                VALUES (?, ?, now(), now(), 0)
                ON CONFLICT (name) DO NOTHING
                """, categoryId, CATEGORY_NAME);

        // Create a test Kafka consumer subscribed to the topic BEFORE producing
        consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName()
        ));
        consumer.subscribe(List.of(TOPIC));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
        // Clean up seeded data to avoid conflicts across test runs
        jdbc.update("DELETE FROM products WHERE name = ?", PRODUCT_NAME);
        jdbc.update("DELETE FROM categories WHERE name = ?", CATEGORY_NAME);
    }

    @Test
    @WithMockUser(authorities = "SCOPE_catalog.write")
    void should_publishEventWithPayloadAndHeaders_when_productIsCreated() throws Exception {
        // Given: a product command
        CreateProductCommand cmd = new CreateProductCommand(
                PRODUCT_NAME, "Wireless USB mouse",
                BigDecimal.valueOf(29.99), true, "Bluetooth 5.0",
                CATEGORY_NAME);

        // When: the product is created via the service within a single transaction
        txTemplate.executeWithoutResult(status ->
                productService.createProduct(cmd));

        // Then: a Kafka record arrives with enriched payload and trace headers
        CopyOnWriteArrayList<ConsumerRecord<String, byte[]>> consumed = new CopyOnWriteArrayList<>();

        await().atMost(10, SECONDS)
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(100));
                    for (ConsumerRecord<String, byte[]> r : records) {
                        consumed.add(r);
                    }
                    assertThat(consumed).isNotEmpty();
                });

        ConsumerRecord<String, byte[]> record = consumed.getFirst();

        // -- Payload assertions --
        ProductEvent.ProductCreated event = MAPPER.readValue(
                record.value(), ProductEvent.ProductCreated.class);

        assertThat(event.productName()).isEqualTo(PRODUCT_NAME);
        assertThat(event.productId()).isNotBlank();
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.occurredAt()).isNotNull();

        // Record key must equal the productId in the payload
        assertThat(record.key()).isEqualTo(event.productId());

        // -- Header assertions --
        assertThat(headerValue(record, "eventType")).isEqualTo("created");
        assertThat(headerValue(record, "correlationId")).isEqualTo(event.eventId());

        Header traceIdHeader = record.headers().lastHeader("traceId");
        assertThat(traceIdHeader).as("traceId header must be present").isNotNull();
    }

    private static String headerValue(ConsumerRecord<?, ?> record, String key) {
        Header header = record.headers().lastHeader(key);
        assertThat(header).as("Header '%s' must be present", key).isNotNull();
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
