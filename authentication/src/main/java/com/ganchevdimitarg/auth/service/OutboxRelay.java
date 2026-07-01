package com.ganchevdimitarg.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.auth.dao.OutboxEventRepository;
import com.ganchevdimitarg.auth.domain.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Scheduled relay for the transactional outbox. Polls PENDING {@link OutboxEvent} rows and
 * publishes each to Kafka with {@code traceId}/{@code correlationId} headers, flipping the
 * row to PUBLISHED on success. A send failure leaves the row PENDING for the next tick, and
 * each row is processed in its own try/catch so one failure never stops the batch.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String HEADER_TRACE_ID = "traceId";
    private static final String HEADER_CORRELATION_ID = "correlationId";
    // security: the raw reset token must never persist in a PUBLISHED row, since those rows
    // are never purged; the real payload is still what gets sent to Kafka.
    private static final String REDACTED_PAYLOAD = "{\"redacted\":true}";

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:2000}")
    public void publishPending() {
        List<OutboxEvent> pending =
                repository.findTop100ByStatusAndDeletedAtIsNullOrderByCreatedAtAsc(STATUS_PENDING);
        for (OutboxEvent event : pending) {
            publishOne(event);
        }
    }

    private void publishOne(OutboxEvent event) {
        try {
            // The producer serialises Object values to JSON; send the already-parsed structure so
            // the message is a JSON object matching the event shape (a String would double-encode).
            // Parse into a plain Map/List graph (not a Jackson JsonNode): the producer's
            // JacksonJsonSerializer is Jackson 3, which would serialise a Jackson 2 JsonNode as a POJO.
            Object payload = objectMapper.readValue(event.getPayload(), Object.class);

            ProducerRecord<String, Object> record =
                    new ProducerRecord<>(event.getTopic(), null, event.getMessageKey(), payload);
            addHeaders(record.headers(), event);

            kafkaTemplate.send(record).get();

            event.setStatus(STATUS_PUBLISHED);
            event.setPublishedAt(Instant.now());
            redactSensitivePayload(event);
            repository.save(event);
            log.info("Published outbox event {} to topic {}", event.getId(), event.getTopic());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Interrupted publishing outbox event {}; leaving PENDING", event.getId(), ex);
        } catch (Exception ex) {
            log.error("Failed to publish outbox event {}; leaving PENDING for retry", event.getId(), ex);
        }
    }

    // security: the reset-request payload carries the raw token; once published, overwrite the
    // persisted row so the raw token no longer sits readable in the database indefinitely. Only
    // this topic is sensitive — register/delete payloads are kept intact for debuggability.
    private void redactSensitivePayload(OutboxEvent event) {
        if (AuthTopics.PASSWORD_RESET_REQUESTED.equals(event.getTopic())) {
            event.setPayload(REDACTED_PAYLOAD);
        }
    }

    private void addHeaders(Headers headers, OutboxEvent event) {
        if (event.getTraceId() != null) {
            headers.add(HEADER_TRACE_ID, event.getTraceId().getBytes(StandardCharsets.UTF_8));
        }
        if (event.getCorrelationId() != null) {
            headers.add(HEADER_CORRELATION_ID, event.getCorrelationId().getBytes(StandardCharsets.UTF_8));
        }
    }
}
