package com.ganchevdimitarg.catalog.event;

import com.ganchevdimitarg.catalog.domain.OutboxEvent;
import com.ganchevdimitarg.catalog.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Relays transactional-outbox rows to Kafka.
 *
 * <p>Each run claims a batch of due rows inside a transaction and marks a row sent only once
 * the broker has acknowledged the record. A failed send leaves its row unsent and stops the
 * batch; retry and backoff are added in TODO 4.4d.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelay {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;

    /**
     * Delivers one batch of due rows, oldest first.
     *
     * @return the number of rows marked sent
     */
    @Transactional
    public int relayOnce() {
        List<OutboxEvent> batch = repository.lockUnsentBatch(properties.batchSize());
        int sent = 0;
        for (OutboxEvent event : batch) {
            try {
                kafkaTemplate.send(toRecord(event))
                        .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while relaying outbox event {} to {}; stopping the batch",
                        event.getId(), event.getTopic(), e);
                break;
            } catch (Exception e) {
                log.error("Failed to relay outbox event {} to {}; leaving the row unsent and stopping the batch",
                        event.getId(), event.getTopic(), e);
                break;
            }
            event.setSentAt(Instant.now());
            sent++;
        }
        return sent;
    }

    private static ProducerRecord<String, String> toRecord(OutboxEvent event) {
        ProducerRecord<String, String> record =
                new ProducerRecord<>(event.getTopic(), event.getMessageKey(), event.getPayload());
        for (Map.Entry<String, String> header : event.getHeaders().entrySet()) {
            record.headers().add(header.getKey(), header.getValue().getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }
}
