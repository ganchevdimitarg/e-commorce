package com.ganchevdimitarg.catalog.event;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import com.ganchevdimitarg.catalog.domain.OutboxEvent;
import com.ganchevdimitarg.catalog.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Failure and recovery semantics of the outbox relay against real Postgres.
 *
 * <p>A broker outage is simulated with a failing {@link org.springframework.kafka.core.KafkaTemplate}
 * rather than by stopping the Testcontainers Kafka broker: stopping the broker changes its
 * mapped ports and makes the shared context unusable, while a mock exercises exactly the
 * relay's failure path deterministically. Kafka itself is never contacted, so this class
 * needs Postgres only.
 */
@Tag("integration")
class OutboxRelayFailureIT extends AbstractIntegrationTest {

    @MockitoBean
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    OutboxRelay relay;

    @Autowired
    OutboxEventRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MeterRegistry meterRegistry;

    private String aggregateId;

    @BeforeEach
    void setUp() {
        aggregateId = UUID.randomUUID().toString();
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM outbox_events WHERE aggregate_id = ?", aggregateId);
    }

    @Test
    void should_leaveRowWithBackoff_when_sendFails() {
        seedRow();
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenThrow(new KafkaException("broker down"));

        assertThat(relay.relayOnce()).isZero();

        assertThat(isUnsent()).isTrue();
        assertThat(queryInt("attempts")).isEqualTo(1);
        assertThat(queryString("last_error")).contains("broker down");
        assertThat(nextAttemptIsInTheFuture()).isTrue();

        // The row is not due yet, so an immediate second run must not claim it again.
        assertThat(relay.relayOnce()).isZero();
        assertThat(queryInt("attempts")).isEqualTo(1);
        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    void should_deliverAfterRecovery_when_backoffElapsed() {
        seedRow();
        double failedBefore = meterRegistry.counter("catalog.outbox.failed").count();
        double publishedBefore = meterRegistry.counter("catalog.outbox.published").count();

        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenThrow(new KafkaException("broker down"));
        assertThat(relay.relayOnce()).isZero();
        assertThat(nextAttemptIsInTheFuture()).isTrue();

        jdbc.update("UPDATE outbox_events SET next_attempt_at = now() - interval '1 second' "
                + "WHERE aggregate_id = ?", aggregateId);
        doReturn(CompletableFuture.completedFuture(null))
                .when(kafkaTemplate).send(any(ProducerRecord.class));

        assertThat(relay.relayOnce()).isEqualTo(1);

        assertThat(isUnsent()).isFalse();
        assertThat(queryInt("attempts")).isEqualTo(1);
        assertThat(meterRegistry.counter("catalog.outbox.failed").count() - failedBefore)
                .isEqualTo(1.0);
        assertThat(meterRegistry.counter("catalog.outbox.published").count() - publishedBefore)
                .isEqualTo(1.0);
        assertThat(meterRegistry.get("catalog.outbox.pending").gauge().value())
                .isEqualTo(repository.countBySentAtIsNullAndDeadLetteredAtIsNull());
    }

    @Test
    void should_parkRowOnce_when_payloadCannotBeSerialised() {
        seedRow();
        Timestamp nextAttemptBefore = queryTimestamp("next_attempt_at");
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new SerializationException("poison payload")));

        assertThat(relay.relayOnce()).isZero();

        assertThat(isUnsent()).isTrue();
        assertThat(queryInt("attempts")).isEqualTo(1);
        assertThat(queryTimestamp("dead_lettered_at")).isNotNull();
        assertThat(queryString("dead_letter_reason")).isNotBlank();
        assertThat(queryTimestamp("next_attempt_at")).isEqualTo(nextAttemptBefore);

        // The claim query excludes the parked row, so a second run must not retry it.
        assertThat(relay.relayOnce()).isZero();
        assertThat(queryInt("attempts")).isEqualTo(1);
        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }

    private void seedRow() {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("Product");
        event.setAggregateId(aggregateId);
        event.setEventType("created");
        event.setTopic(ProductTopics.PRODUCT_CREATED);
        event.setMessageKey(aggregateId);
        event.setPayload("{\"productName\":\"failure-it-mouse\"}");
        event.setHeaders(Map.of("eventType", "created"));
        event.setAttempts(0);
        event.setNextAttemptAt(Instant.now().minusSeconds(1));
        event.setCreatedAt(Instant.now());
        event.setSentAt(null);
        repository.saveAndFlush(event);
    }

    private boolean isUnsent() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT sent_at IS NULL FROM outbox_events WHERE aggregate_id = ?",
                Boolean.class, aggregateId));
    }

    private boolean nextAttemptIsInTheFuture() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT next_attempt_at > now() FROM outbox_events WHERE aggregate_id = ?",
                Boolean.class, aggregateId));
    }

    private int queryInt(String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM outbox_events WHERE aggregate_id = ?",
                Integer.class, aggregateId);
    }

    private String queryString(String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM outbox_events WHERE aggregate_id = ?",
                String.class, aggregateId);
    }

    private Timestamp queryTimestamp(String column) {
        return jdbc.queryForObject(
                "SELECT " + column + " FROM outbox_events WHERE aggregate_id = ?",
                Timestamp.class, aggregateId);
    }
}
