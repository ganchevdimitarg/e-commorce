package com.concordeu.catalog.event;

import com.concordeu.catalog.RedisKafkaIntegrationBase;
import com.concordeu.catalog.dto.product.CreateProductCommand;
import com.concordeu.catalog.service.product.ProductService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
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
 * the corresponding {@code catalog.product.created} Kafka event arrives on the topic.
 */
@Tag("integration")
class ProductEventIT extends RedisKafkaIntegrationBase {

    private static final String TOPIC = "catalog.product.created";
    private static final String PRODUCT_NAME = "mouse";
    private static final String CATEGORY_NAME = "peripherals";

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
        String categoryId = UUID.randomUUID().toString();
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
    void should_publishCreatedEvent_when_productIsCreated() {
        // Given: a product command
        CreateProductCommand cmd = new CreateProductCommand(
                PRODUCT_NAME, "Wireless USB mouse",
                BigDecimal.valueOf(29.99), true, "Bluetooth 5.0",
                CATEGORY_NAME);

        // When: the product is created via the service within a single transaction
        // (createProduct lacks @Transactional, so we wrap it to keep the Category managed)
        txTemplate.executeWithoutResult(status ->
                productService.createProduct(cmd));

        // Then: a Kafka record with key "mouse" arrives on the topic
        CopyOnWriteArrayList<String> consumedKeys = new CopyOnWriteArrayList<>();

        await().atMost(10, SECONDS)
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(100));
                    for (ConsumerRecord<String, byte[]> record : records) {
                        consumedKeys.add(record.key());
                    }
                    assertThat(consumedKeys).contains(PRODUCT_NAME);
                });
    }
}
