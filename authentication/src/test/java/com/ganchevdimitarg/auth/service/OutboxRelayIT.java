package com.ganchevdimitarg.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.auth.AbstractIntegrationTest;
import com.ganchevdimitarg.auth.dao.OutboxEventRepository;
import com.ganchevdimitarg.auth.domain.OutboxEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class OutboxRelayIT extends AbstractIntegrationTest {

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    private Consumer<String, String> consumer;

    @AfterEach
    void closeConsumer() {
        if (consumer != null) {
            consumer.close();
            consumer = null;
        }
    }

    @Test
    void should_publishPendingRowWithTraceHeaders_andFlipToPublished() throws Exception {
        String userId = UUID.randomUUID().toString();
        String traceId = "trace-" + UUID.randomUUID();
        String correlationId = "corr-" + UUID.randomUUID();

        consumer = newConsumer(AuthTopics.USER_REGISTERED);

        OutboxEvent row = new OutboxEvent();
        UUID rowId = UUID.randomUUID();
        row.setId(rowId);
        row.setAggregateType("user");
        row.setAggregateId(userId);
        row.setTopic(AuthTopics.USER_REGISTERED);
        row.setMessageKey(userId);
        row.setPayload("{\"userId\":\"" + userId + "\",\"email\":\"relay@test.io\"}");
        row.setTraceId(traceId);
        row.setCorrelationId(correlationId);
        row.setStatus("PENDING");
        repository.save(row);

        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        await().atMost(30, SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            records.records(AuthTopics.USER_REGISTERED).forEach(r -> {
                if (userId.equals(r.key())) {
                    matching.add(r);
                }
            });
            assertThat(matching).isNotEmpty();
        });

        ConsumerRecord<String, String> published = matching.get(0);
        assertThat(published.value()).contains(userId).contains("relay@test.io");

        JsonNode payload = objectMapper.readTree(published.value());
        assertThat(payload.isObject())
                .as("payload must be a JSON object, not a double-encoded JSON string: %s", published.value())
                .isTrue();
        assertThat(payload.get("userId").asText()).isEqualTo(userId);
        assertThat(payload.get("email").asText()).isEqualTo("relay@test.io");

        assertThat(headerValue(published, "traceId")).isEqualTo(traceId);
        assertThat(headerValue(published, "correlationId")).isEqualTo(correlationId);

        await().atMost(10, SECONDS).untilAsserted(() ->
                assertThat(repository.findById(rowId))
                        .get()
                        .satisfies(r -> {
                            assertThat(r.getStatus()).isEqualTo("PUBLISHED");
                            assertThat(r.getPublishedAt()).isNotNull();
                            // Non-sensitive topic: the persisted payload is kept intact for debuggability.
                            assertThat(r.getPayload()).contains(userId).contains("relay@test.io");
                        }));
    }

    @Test
    void should_redactPersistedPayload_when_topicIsPasswordResetRequested() throws Exception {
        String userId = UUID.randomUUID().toString();
        String rawToken = "raw-token-" + UUID.randomUUID();

        consumer = newConsumer(AuthTopics.PASSWORD_RESET_REQUESTED);

        OutboxEvent row = new OutboxEvent();
        UUID rowId = UUID.randomUUID();
        row.setId(rowId);
        row.setAggregateType("user");
        row.setAggregateId(userId);
        row.setTopic(AuthTopics.PASSWORD_RESET_REQUESTED);
        row.setMessageKey(userId);
        row.setPayload("{\"userId\":\"" + userId + "\",\"email\":\"reset@test.io\",\"rawToken\":\""
                + rawToken + "\",\"expiresAt\":\"2030-01-01T00:00:00Z\",\"occurredAt\":\"2026-01-01T00:00:00Z\"}");
        row.setStatus("PENDING");
        repository.save(row);

        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        await().atMost(30, SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            records.records(AuthTopics.PASSWORD_RESET_REQUESTED).forEach(r -> {
                if (userId.equals(r.key())) {
                    matching.add(r);
                }
            });
            assertThat(matching).isNotEmpty();
        });

        // The message actually SENT to Kafka must still carry the real raw token.
        assertThat(matching.get(0).value()).contains(rawToken);

        await().atMost(10, SECONDS).untilAsserted(() ->
                assertThat(repository.findById(rowId))
                        .get()
                        .satisfies(r -> {
                            assertThat(r.getStatus()).isEqualTo("PUBLISHED");
                            // Sensitive topic: the persisted PUBLISHED row must no longer hold the raw token.
                            assertThat(r.getPayload()).doesNotContain(rawToken);
                            assertThat(r.getPayload()).contains("redacted");
                        }));
    }

    private String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}
