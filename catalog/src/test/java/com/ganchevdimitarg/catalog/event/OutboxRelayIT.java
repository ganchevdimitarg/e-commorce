package com.ganchevdimitarg.catalog.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.catalog.RedisKafkaIntegrationBase;
import com.ganchevdimitarg.catalog.domain.Product;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end relay test against real Postgres, Redis and Kafka (Testcontainers).
 *
 * <p>A row written through {@link OutboxEventWriter} must reach
 * {@code catalog.product.created} exactly once, keyed by product id, with its stored headers
 * preserved — and a second relay run must not redeliver it. Scheduled runs are disabled by
 * {@code catalog.outbox.enabled=false} in the test profile, so the manual calls are the only
 * driver.
 */
@Tag("integration")
class OutboxRelayIT extends RedisKafkaIntegrationBase {

    private static final String PRODUCT_NAME = "relay-mouse";
    private static final String DESCRIPTION = "Relay integration test product";
    private static final String CATEGORY_NAME = "relay-peripherals";

    @Autowired
    OutboxRelay relay;

    @Autowired
    OutboxEventWriter writer;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate txTemplate;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${spring.kafka.bootstrap-servers}")
    String bootstrapServers;

    private UUID categoryId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        categoryId = UUID.randomUUID();
        productId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO categories (id, name, created_at, updated_at, version)
                VALUES (?, ?, now(), now(), 0)
                """, categoryId, CATEGORY_NAME);
        jdbc.update("""
                INSERT INTO products
                    (id, name, description, price, stock, characteristics, category_id, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, true, ?, ?, now(), now(), 0)
                """, productId, PRODUCT_NAME, DESCRIPTION, BigDecimal.valueOf(9.99), "test", categoryId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM outbox_events WHERE aggregate_id = ?", productId.toString());
        jdbc.update("DELETE FROM products WHERE id = ?", productId);
        jdbc.update("DELETE FROM categories WHERE id = ?", categoryId);
    }

    @Test
    void should_deliverRowAndMarkSent_when_relayRuns() throws Exception {
        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of(ProductTopics.PRODUCT_CREATED));

            writeCreatedRow();

            assertThat(relay.relayOnce()).isEqualTo(1);

            AtomicReference<ConsumerRecord<String, String>> received = new AtomicReference<>();
            await().atMost(15, SECONDS).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (productId.toString().equals(record.key())) {
                        received.set(record);
                    }
                });
                assertThat(received.get()).isNotNull();
            });

            ConsumerRecord<String, String> record = received.get();
            // jsonb reformats the stored payload, so assert on parsed fields, not bytes.
            JsonNode payload = objectMapper.readTree(record.value());
            assertThat(payload.get("productName").asText()).isEqualTo(PRODUCT_NAME);
            assertThat(payload.get("productId").asText()).isEqualTo(productId.toString());
            assertThat(header(record, "eventType")).isEqualTo("created");
            assertThat(header(record, "correlationId")).isNotBlank();
            assertThat(isSent(productId)).isTrue();
        }
    }

    @Test
    void should_notRedeliver_when_relayRunsTwice() {
        writeCreatedRow();

        assertThat(relay.relayOnce()).isEqualTo(1);
        assertThat(relay.relayOnce()).isZero();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND sent_at IS NOT NULL",
                Integer.class, productId.toString())).isEqualTo(1);
    }

    private void writeCreatedRow() {
        Product product = new Product();
        product.setId(productId);
        product.setName(PRODUCT_NAME);
        txTemplate.executeWithoutResult(status -> writer.writeCreated(product));
    }

    private boolean isSent(UUID aggregateId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT sent_at IS NOT NULL FROM outbox_events WHERE aggregate_id = ?",
                Boolean.class, aggregateId.toString()));
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private KafkaConsumer<String, String> consumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-relay-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }
}
