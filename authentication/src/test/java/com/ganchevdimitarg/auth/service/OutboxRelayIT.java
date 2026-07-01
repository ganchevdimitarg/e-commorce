package com.ganchevdimitarg.auth.service;

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

    private Consumer<String, String> consumer;

    @AfterEach
    void closeConsumer() {
        if (consumer != null) {
            consumer.close();
            consumer = null;
        }
    }

    @Test
    void should_publishPendingRowWithTraceHeaders_andFlipToPublished() {
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
        assertThat(headerValue(published, "traceId")).isEqualTo(traceId);
        assertThat(headerValue(published, "correlationId")).isEqualTo(correlationId);

        await().atMost(10, SECONDS).untilAsserted(() ->
                assertThat(repository.findById(rowId))
                        .get()
                        .satisfies(r -> {
                            assertThat(r.getStatus()).isEqualTo("PUBLISHED");
                            assertThat(r.getPublishedAt()).isNotNull();
                        }));
    }

    private String headerValue(ConsumerRecord<String, String> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header != null ? new String(header.value(), StandardCharsets.UTF_8) : null;
    }
}
