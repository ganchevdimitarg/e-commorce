package com.ganchevdimitarg.catalog.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.catalog.RedisKafkaIntegrationBase;
import com.ganchevdimitarg.catalog.domain.OutboxEvent;
import com.ganchevdimitarg.catalog.repository.OutboxEventRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Proves the claim query preserves per-aggregate order when two relay instances run at once.
 *
 * <p>Two virtual threads drive {@link OutboxRelay#relayOnce()} over interleaved rows for
 * three aggregates. The claim admits at most one in-flight row per {@code aggregate_id}, so
 * each aggregate's events must reach the broker in the order they were written even though
 * the two relay runs compete for rows.
 */
@Tag("integration")
class OutboxRelayConcurrencyIT extends RedisKafkaIntegrationBase {

    private static final int PER_AGGREGATE = 4;
    private static final int AGGREGATES = 3;

    @Autowired
    OutboxRelay relay;

    @Autowired
    OutboxEventRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${spring.kafka.bootstrap-servers}")
    String bootstrapServers;

    private final List<String> aggregateIds = new ArrayList<>();

    @BeforeEach
    void seedInterleavedRows() {
        Instant base = Instant.now().minusSeconds(120);
        for (int aggregate = 0; aggregate < AGGREGATES; aggregate++) {
            String aggregateId = UUID.randomUUID().toString();
            aggregateIds.add(aggregateId);
            for (int seq = 0; seq < PER_AGGREGATE; seq++) {
                OutboxEvent event = new OutboxEvent();
                event.setAggregateType("Product");
                event.setAggregateId(aggregateId);
                event.setEventType("created");
                event.setTopic(ProductTopics.PRODUCT_CREATED);
                event.setMessageKey(aggregateId);
                event.setPayload("{\"seq\":" + seq + "}");
                event.setHeaders(Map.of("eventType", "created"));
                event.setAttempts(0);
                event.setNextAttemptAt(base);
                event.setCreatedAt(base.plusSeconds(seq));
                repository.saveAndFlush(event);
            }
        }
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM outbox_events WHERE aggregate_id IN ("
                + String.join(",", aggregateIds.stream().map(id -> "'" + id + "'").toList())
                + ")");
    }

    @Test
    void should_deliverInOrderPerAggregate_when_twoRelaysRunConcurrently() throws Exception {
        try (KafkaConsumer<String, String> consumer = consumer();
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            consumer.subscribe(List.of(ProductTopics.PRODUCT_CREATED));
            Future<?> first = executor.submit(this::drain);
            Future<?> second = executor.submit(this::drain);
            first.get(30, SECONDS);
            second.get(30, SECONDS);

            Map<String, List<Integer>> sequences = new HashMap<>();
            await().atMost(30, SECONDS).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (aggregateIds.contains(record.key())) {
                        try {
                            sequences.computeIfAbsent(record.key(), key -> new ArrayList<>())
                                    .add(objectMapper.readTree(record.value()).get("seq").asInt());
                        } catch (JsonProcessingException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                });
                assertThat(sequences).hasSize(AGGREGATES);
                sequences.values().forEach(seq ->
                        assertThat(seq).containsExactly(0, 1, 2, 3));
            });
        }
    }

    private void drain() {
        for (int run = 0; run < 50; run++) {
            relay.relayOnce();
        }
    }

    private KafkaConsumer<String, String> consumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-order-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }
}
