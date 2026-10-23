package com.ganchevdimitarg.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A transactional outbox row holding a product event until the relay delivers it.
 *
 * <p>Deliberately not an {@link Auditable}: outbox rows are operational delivery state, not
 * an audited record — the relay mutates {@code attempts}, {@code last_error},
 * {@code next_attempt_at}, {@code sent_at} and the dead-letter columns — so the
 * audit/soft-delete/version columns do not apply.
 */
@Entity(name = "OutboxEvent")
@Table(name = "outbox_events")
@NoArgsConstructor
@Getter
@Setter
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Column(name = "message_key", nullable = false, length = 200)
    private String messageKey;

    // The writer stores serialised event JSON; it is kept as a String so the relay
    // republishes the exact bytes rather than re-serialising a polymorphic payload.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> headers;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    // The relay instance currently responsible for this row. NULL means unclaimed; an
    // expired value makes the row claimable again.
    @Column(name = "claimed_until")
    private Instant claimedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    // Dead-letter state: a non-retryable failure parks the row here for an operator to
    // inspect instead of retrying it forever. Dead-lettered rows are never claimed again.
    @Column(name = "dead_lettered_at")
    private Instant deadLetteredAt;

    @Column(name = "dead_letter_reason", columnDefinition = "TEXT")
    private String deadLetterReason;
}
