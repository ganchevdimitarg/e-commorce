package com.ganchevdimitarg.catalog.event;

import com.ganchevdimitarg.catalog.domain.OutboxEvent;
import com.ganchevdimitarg.catalog.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.InvalidRecordException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Relays transactional-outbox rows to Kafka.
 *
 * <p>Each run claims a batch of due rows inside a transaction and marks a row sent only once
 * the broker has acknowledged the record. A retryable failed send records the attempt and
 * stops the batch; the row is retried after an exponential backoff capped at 60 seconds.
 * Delivery is at-least-once: a crash between the broker acknowledgement and the commit
 * resends the row, and consumers deduplicate on the event's {@code eventId}.
 *
 * <p>Non-retryable ("poison") failures — a payload that cannot be serialised, an oversized
 * record, an invalid record — are dead-lettered in the database instead: the row still
 * records the attempt and its error, gains {@code dead_lettered_at}/{@code
 * dead_letter_reason}, and is excluded from future claims. Broker and network failures
 * always retry, so an outage never dead-letters healthy events.
 */
@Slf4j
@Component
public class OutboxRelay {

    static final String PUBLISHED_METRIC = "catalog.outbox.published";
    static final String FAILED_METRIC = "catalog.outbox.failed";
    static final String PENDING_METRIC = "catalog.outbox.pending";
    static final String DEAD_LETTER_METRIC = "catalog.outbox.dead_lettered";

    private static final int MAX_ERROR_LENGTH = 1000;

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;
    private final MeterRegistry meterRegistry;
    private final AtomicLong pending = new AtomicLong();

    public OutboxRelay(OutboxEventRepository repository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       OutboxProperties properties,
                       MeterRegistry meterRegistry) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        meterRegistry.gauge(PENDING_METRIC, pending);
    }

    /**
     * Delivers one batch of due rows, oldest first.
     *
     * <p>A failed send records the attempt and its error and stops the batch, so a down
     * broker is probed once per run rather than once per row. A retryable failure schedules
     * the next attempt with exponential backoff (capped at 60 seconds); a poison failure
     * dead-letters the row instead. Rows that are not yet due, and dead-lettered rows, are
     * skipped by the claim query. An interrupt is not an attempt: the row stays due and the
     * thread's interrupt flag is restored.
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
                event.setSentAt(Instant.now());
                sent++;
                meterRegistry.counter(PUBLISHED_METRIC).increment();
            } catch (InterruptedException e) {
                // Not an attempt: leave the row due for the next run and stop the batch.
                Thread.currentThread().interrupt();
                log.error("Interrupted while relaying outbox event {} to {}; stopping the batch",
                        event.getId(), event.getTopic(), e);
                break;
            } catch (Exception e) {
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(truncate(e, MAX_ERROR_LENGTH));
                if (isPoison(e)) {
                    event.setDeadLetteredAt(Instant.now());
                    event.setDeadLetterReason(truncate(e, MAX_ERROR_LENGTH));
                    meterRegistry.counter(DEAD_LETTER_METRIC).increment();
                    log.error("Outbox event {} dead-lettered: {}", event.getId(), e.getMessage(), e);
                } else {
                    event.setNextAttemptAt(Instant.now().plus(backoff(event.getAttempts())));
                    meterRegistry.counter(FAILED_METRIC).increment();
                    log.error("Outbox send failed for {} (attempt {}): {}", event.getId(),
                            event.getAttempts(), e.getMessage(), e);
                }
                break;
            }
        }
        pending.set(repository.countBySentAtIsNullAndDeadLetteredAtIsNull());
        return sent;
    }

    /**
     * Whether a send failure can never succeed on retry and must be dead-lettered.
     *
     * <p>Serialisation, oversized-record and invalid-record errors describe the payload
     * itself, so resending it unchanged cannot work; an {@link IllegalArgumentException} is a
     * programming error in the record (for example a null topic). The cause chain is walked
     * because {@code KafkaTemplate.send(...).get(...)} wraps the producer's failure in an
     * {@link java.util.concurrent.ExecutionException}. Broker, network and timeout failures
     * are deliberately not poison: they retry with the existing backoff.
     *
     * @param error the failure thrown while sending the row
     */
    static boolean isPoison(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof SerializationException
                    || t instanceof RecordTooLargeException
                    || t instanceof InvalidRecordException
                    || t instanceof IllegalArgumentException) {
                return true;
            }
        }
        return false;
    }

    /**
     * Exponential backoff for a failed delivery, capped at 60 seconds:
     * 1 s, 2 s, 4 s, 8 s, 16 s, 32 s, then 60 s.
     *
     * @param attempts the number of attempts made so far (at least 1)
     */
    static Duration backoff(int attempts) {
        return Duration.ofSeconds(Math.min(60, 1L << Math.min(attempts - 1, 6)));
    }

    /**
     * Renders a failure message for {@code last_error}, falling back to the exception type
     * when the throwable has no message, and truncating it to the column's practical limit.
     */
    static String truncate(Throwable error, int maxLength) {
        String message = error.getMessage() == null
                ? error.getClass().getSimpleName()
                : error.getMessage();
        return message.length() <= maxLength ? message : message.substring(0, maxLength);
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
