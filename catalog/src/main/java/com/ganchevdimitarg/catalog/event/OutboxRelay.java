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
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Relays transactional-outbox rows to Kafka in three phases.
 *
 * <p>A short claim transaction selects the oldest due rows and sets {@code claimed_until}; the
 * sends are then issued outside any transaction and awaited; a second short transaction marks
 * each claimed row sent, records its failure, or dead-letters it, and clears the claim. No
 * database connection or row lock is therefore held during Kafka I/O.
 *
 * <p>The claim query admits at most one in-flight row per {@code aggregate_id}, so a
 * product's events reach Kafka in the order they were written even with several relay
 * instances. An active lease is skipped by other instances; an expired lease makes a crashed
 * claim retryable. Delivery is at-least-once: a crash after the broker acknowledgement but
 * before the mark resends the row, and consumers deduplicate on the event's {@code eventId}.
 *
 * <p>Non-retryable ("poison") failures — a payload that cannot be serialised, an oversized
 * record, an invalid record — are dead-lettered in the database instead: the row records the
 * attempt and its error, gains {@code dead_lettered_at}/{@code dead_letter_reason}, and is
 * excluded from future claims. Broker and network failures always retry, so an outage never
 * dead-letters healthy events.
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
    private final TransactionTemplate transactionTemplate;
    private final AtomicLong pending = new AtomicLong();

    public OutboxRelay(OutboxEventRepository repository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       OutboxProperties properties,
                       MeterRegistry meterRegistry,
                       TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.transactionTemplate = transactionTemplate;
        meterRegistry.gauge(PENDING_METRIC, pending);
    }

    /**
     * Delivers one batch of due rows, oldest first.
     *
     * <p>The claim runs in a short transaction that sets the lease. Every claimed send is then
     * issued before any future is awaited, outside any transaction, so a down broker is
     * charged to every claimed row rather than aborting the batch on the first failure. In a
     * second short transaction each row is marked sent, its retryable failure is recorded with
     * exponential backoff (capped at 60 seconds), or a poison failure dead-letters it. An
     * interrupt is not an attempt: the remaining rows keep their claim released and stay due
     * for the next run, and the thread's interrupt flag is restored.
     *
     * @return the number of rows marked sent
     */
    public int relayOnce() {
        List<OutboxEvent> batch = transactionTemplate.execute(status ->
                repository.claimDueBatch(properties.batchSize(), leaseUntil()));
        if (batch == null || batch.isEmpty()) {
            refreshPending();
            return 0;
        }
        List<SendOutcome> outcomes = sendAll(batch);
        Integer sent = transactionTemplate.execute(status -> markOutcomes(batch, outcomes));
        refreshPending();
        return sent == null ? 0 : sent;
    }

    /**
     * Issues every send before awaiting any future. A synchronous send failure becomes a
     * failed future so each claimed row still records an outcome.
     */
    private List<SendOutcome> sendAll(List<OutboxEvent> batch) {
        List<CompletableFuture<SendResult<String, String>>> futures = new ArrayList<>(batch.size());
        for (OutboxEvent event : batch) {
            try {
                futures.add(kafkaTemplate.send(toRecord(event)));
            } catch (RuntimeException e) {
                futures.add(CompletableFuture.failedFuture(e));
            }
        }
        List<SendOutcome> outcomes = new ArrayList<>(batch.size());
        for (int i = 0; i < batch.size(); i++) {
            OutboxEvent event = batch.get(i);
            try {
                futures.get(i).get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                outcomes.add(new SendOutcome(event, null));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while relaying outbox event {}; releasing the batch",
                        event.getId(), e);
                break;
            } catch (ExecutionException e) {
                // get() wraps the producer's failure; record the cause, not the wrapper.
                outcomes.add(new SendOutcome(event, e.getCause() == null ? e : e.getCause()));
            } catch (Exception e) {
                outcomes.add(new SendOutcome(event, e));
            }
        }
        return outcomes;
    }

    /**
     * Applies the recorded outcomes in a second short transaction. Every claimed row has its
     * lease cleared — including rows after an interrupt, which have no outcome — and rows
     * without an outcome stay due for the next run.
     *
     * @return the number of rows marked sent
     */
    private int markOutcomes(List<OutboxEvent> claimed, List<SendOutcome> outcomes) {
        Map<UUID, SendOutcome> byId = outcomes.stream()
                .collect(Collectors.toMap(outcome -> outcome.event().getId(), Function.identity()));
        int sent = 0;
        for (OutboxEvent row : repository.findAllById(
                claimed.stream().map(OutboxEvent::getId).toList())) {
            row.setClaimedUntil(null);
            SendOutcome outcome = byId.get(row.getId());
            if (outcome == null) {
                continue;
            }
            if (outcome.failure() == null) {
                row.setSentAt(Instant.now());
                meterRegistry.counter(PUBLISHED_METRIC).increment();
                sent++;
            } else {
                recordFailure(row, outcome.failure());
            }
        }
        return sent;
    }

    /**
     * Records a failed attempt: the row gains the attempt and its error, and either schedules
     * the next attempt with backoff or, for a poison failure, is parked as a dead letter.
     */
    private void recordFailure(OutboxEvent event, Throwable failure) {
        event.setAttempts(event.getAttempts() + 1);
        event.setLastError(truncate(failure, MAX_ERROR_LENGTH));
        if (isPoison(failure)) {
            event.setDeadLetteredAt(Instant.now());
            event.setDeadLetterReason(truncate(failure, MAX_ERROR_LENGTH));
            meterRegistry.counter(DEAD_LETTER_METRIC).increment();
            log.error("Outbox event {} dead-lettered: {}", event.getId(), failure.getMessage(), failure);
        } else {
            event.setNextAttemptAt(Instant.now().plus(backoff(event.getAttempts())));
            meterRegistry.counter(FAILED_METRIC).increment();
            log.error("Outbox send failed for {} (attempt {}): {}", event.getId(),
                    event.getAttempts(), failure.getMessage(), failure);
        }
    }

    private OffsetDateTime leaseUntil() {
        return OffsetDateTime.now(ZoneOffset.UTC).plus(properties.claimLease());
    }

    private void refreshPending() {
        pending.set(repository.countBySentAtIsNullAndDeadLetteredAtIsNull());
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

    /** The outcome of one claimed send; a {@code null} failure means the broker acknowledged. */
    private record SendOutcome(OutboxEvent event, Throwable failure) {
    }
}
