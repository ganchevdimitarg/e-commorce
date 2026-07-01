package com.ganchevdimitarg.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.auth.dao.OutboxEventRepository;
import com.ganchevdimitarg.auth.domain.OutboxEvent;
import com.ganchevdimitarg.auth.exception.OutboxSerializationException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Writes a PENDING {@link OutboxEvent} row inside the caller's existing {@code @Transactional}
 * so the domain change and the intent to publish commit atomically. A scheduled relay
 * ({@link OutboxRelay}) later publishes the row to Kafka and flips it to PUBLISHED.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxWriter {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;

    /**
     * Serialise {@code event} to JSON and persist it as a PENDING outbox row. Runs within the
     * caller's transaction; a serialisation failure rolls that transaction back rather than
     * committing a domain change whose event could never be published.
     */
    public void write(String topic, String key, Object event, String aggregateType, String aggregateId) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new OutboxSerializationException(
                    "Failed to serialise event for topic " + topic, ex);
        }

        OutboxEvent row = new OutboxEvent();
        row.setId(UUID.randomUUID());
        row.setAggregateType(aggregateType);
        row.setAggregateId(aggregateId);
        row.setTopic(topic);
        row.setMessageKey(key);
        row.setPayload(payload);
        row.setTraceId(currentTraceId());
        row.setCorrelationId(currentSpanId());
        row.setStatus("PENDING");

        repository.save(row);
        log.debug("Queued outbox event for topic {} key {}", topic, key);
    }

    private String currentTraceId() {
        Span span = tracer.currentSpan();
        if (span != null) {
            return span.context().traceId();
        }
        String mdcTraceId = MDC.get("traceId");
        return mdcTraceId != null ? mdcTraceId : "";
    }

    private String currentSpanId() {
        Span span = tracer.currentSpan();
        if (span != null) {
            return span.context().spanId();
        }
        String mdcSpanId = MDC.get("spanId");
        return mdcSpanId != null ? mdcSpanId : "";
    }
}
