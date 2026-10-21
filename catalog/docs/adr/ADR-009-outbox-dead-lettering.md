# ADR-009: Producer-side dead-lettering for poisoned outbox events

**Date:** 2026-10-21
**Status:** Accepted

## Context

Catalog is a producer-only service: it writes product events to the `outbox_events` table in
the business transaction and `OutboxRelay` republishes them to Kafka. ADR-007 deliberately
shipped without dead-letter handling: every send failure incremented `attempts`, stored
`last_error`, backed off (1–60 s) and retried indefinitely.

That is the right behaviour for a broker outage — rows drain once the broker recovers — but
it is wrong for a payload the producer can never publish. A serialisation failure, an
oversized record or an invalid record fails identically on every attempt, so the row keeps
its claim slot at the head of the oldest-first batch and retries forever while an operator
sees only a growing `attempts` count. A Kafka dead-letter topic does not help here: a
payload that fails serialisation cannot be published to a `.DLT` topic either, because
producing to that topic requires the same serialisation step.

## Decision

Dead-lettering is **producer-side and stored in the database**, not in a Kafka `.DLT` topic.

### Classification

`OutboxRelay.isPoison(Throwable)` walks the cause chain (the blocking `send(...).get(...)`
wraps the producer's failure in an `ExecutionException`) and treats these as non-retryable:

- `org.apache.kafka.common.errors.SerializationException`
- `org.apache.kafka.common.errors.RecordTooLargeException`
- `org.apache.kafka.common.InvalidRecordException`
- `IllegalArgumentException` — a programming error in the record, such as a null topic

Broker, network and timeout failures are **not** poison: they keep the ADR-007 backoff and
retry indefinitely, so an outage never dead-letters healthy events.

### Parking the row

On a poison failure the relay still increments `attempts` and stores `last_error`, then:

- sets `dead_lettered_at = now()` and `dead_letter_reason` (the same truncated message);
- increments the `catalog.outbox.dead_lettered` counter and logs at ERROR with the row id
  and reason;
- does **not** schedule `next_attempt_at` — the row is excluded by the claim predicate
  instead;
- stops the batch, matching the existing failure behaviour.

V18 adds `dead_lettered_at`/`dead_letter_reason` and the partial index
`idx_outbox_events_unsent_live` on `(created_at) WHERE sent_at IS NULL AND dead_lettered_at
IS NULL`. `OutboxEventRepository.lockUnsentBatch` excludes dead-lettered rows, and the
`catalog.outbox.pending` gauge counts only live rows
(`countBySentAtIsNullAndDeadLetteredAtIsNull`), so parked rows do not look like a backlog.

The dead-lettered row stays in the table for an operator to inspect and is never deleted
automatically. Re-driving it (after a code fix) is a manual `UPDATE` that clears
`dead_lettered_at`/`dead_letter_reason` and sets `next_attempt_at = now()`.

## Consequences

- **Poison is parked exactly once.** After the run that dead-letters it, the row is never
  claimed again; the metric and ERROR log name the row and reason.
- **Outages still retry.** A broker restart, timeout or network partition leaves rows live
  and retrying; only payload-level failures are parked.
- **No new topic or consumer.** Catalog remains producer-only; there is nothing consuming a
  `.DLT` topic in this service.
- **Operators need a re-drive procedure.** The database row is the dead-letter store, so
  inspection and re-driving happen through SQL, not Kafka tooling. No automated re-drive
  exists in this phase.
- **The distinction is heuristic.** An `IllegalArgumentException` anywhere in the cause chain
  is treated as poison; if a retryable path ever wraps one, the row would be parked and needs
  the manual re-drive above. The set is deliberately narrow.

## Alternatives rejected

- **Kafka `.DLT` topic produced by catalog.** A payload that cannot be serialised cannot be
  published to a dead-letter topic either; and catalog has no consumer, so the topic would
  need new ownership outside this module.
- **Capped retries** (`attempts >= N` → dead-letter). It conflates a long broker outage with a
  poison payload: healthy events would be parked during an outage.
- **Deleting poison rows.** It destroys the evidence an operator needs; the row is retained.
- **Consumer-side DLT in catalog.** There is no `@KafkaListener` in catalog. Dead-letter
  handling for consumed records remains the responsibility of the consuming modules
  (`notification`, and any future consumer).
