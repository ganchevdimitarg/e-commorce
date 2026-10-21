# ADR-007: Transactional outbox for catalog product events

**Date:** 2026-10-20  
**Status:** Accepted

## Context

Before Phase 4 the catalogue published product events with a fire-and-forget send after the
transaction committed. That design has two failure modes:

1. **A failed send loses the event.** The product row is committed, the broker call fails
   (outage, timeout, serialisation error), and nothing retries — downstream services never
   see the change.
2. **A crash after commit loses the event.** The process dies between the commit and the
   send, so the event is never produced even though the state change is durable.

Publishing inside the transaction does not fix this either: the database and the broker
have no shared transaction, so one can commit while the other fails (the dual-write
problem). TODO 4.4 requires durable, at-least-once delivery without adding a distributed
transaction coordinator.

## Decision

### Outbox table written in the business transaction

`OutboxEventWriter` inserts an `outbox_events` row in the **same transaction** as the
product change. The row carries the topic, message key, pre-serialised JSON payload and
headers (`eventType`, `correlationId` (the event's `eventId`) and `traceId`); `eventId` is a
payload field, not a header. If the business transaction rolls back,
the event row rolls back with it; if it commits, the event is durable.

### Scheduled relay

`OutboxRelay` runs from `OutboxScheduler` on a fixed delay (default 1 s, gated by
`catalog.outbox.enabled`). Each run:

- claims a batch (default 20, oldest first) of rows where `sent_at IS NULL` and
  `next_attempt_at <= now()`, using `SELECT … FOR UPDATE SKIP LOCKED` so concurrent relay
  instances claim disjoint batches instead of blocking;
- sends each row's stored bytes through the dedicated `outboxKafkaTemplate`
  (`acks=all`, idempotent producer) and waits up to the configured send timeout;
- marks `sent_at` only after the broker acknowledges, then commits.

Delivery is therefore **at-least-once**.

### Retry, backoff and metrics

A failed send records the failure on the row and stops the batch, so a down broker is
probed once per run rather than once per row:

- `attempts` is incremented;
- `last_error` stores the exception message (falling back to the exception type when it has
  no message), truncated to 1000 characters;
- `next_attempt_at = now + min(60 s, 1 s × 2^(attempts−1))` — 1, 2, 4, 8, 16, 32, then
  60 seconds;
- the batch stops and the transaction commits the retry state.

An `InterruptedException` is not an attempt: the interrupt flag is restored and the row
stays due for the next run. Successful sends increment the `catalog.outbox.published`
counter and failed ones `catalog.outbox.failed`; a `catalog.outbox.pending` gauge reports
`countBySentAtIsNull()` after every run so a growing backlog can be alerted on.

There is **no dead-letter topic in v1**. A poison message retries every 60 seconds
indefinitely; its `last_error` and `attempts` are visible in the table for an operator to
inspect. A DLT is a Phase 5+ concern once consumers and topics are versioned (TODO 4.5).

> **Update (2026-10-21):** ADR-009 supersedes the "retries indefinitely" statement above
> for non-retryable (poison) failures. Those rows are now dead-lettered in the database;
> only retryable broker, network and timeout failures keep retrying with this backoff.

## Consequences

- **Duplicates are possible.** A crash after the broker acknowledgement but before the
  database commit resends the row on the next run. Consumers must deduplicate on the
  payload's `eventId`, which is stable for the row's lifetime.
- **Ordering is best-effort.** Rows are claimed oldest first and a single instance processes
  them in order, but concurrent instances and retries can reorder deliveries per key.
- **Polling adds database load.** One indexed query per second (`idx_outbox_events_unsent`)
  in the default configuration; the interval is configurable.
- **Rows are retained.** `sent_at` keeps delivery history rather than deleting rows; no
  purge job exists yet, so the table grows until an operator adds retention.
- **A broker outage degrades latency, not correctness.** Rows accumulate and drain once the
  broker recovers; the pending gauge exposes the backlog.
- **Tests simulate an outage with a failing `KafkaTemplate`.** Stopping the Testcontainers
  Kafka broker changes its mapped port and makes the shared Spring context unusable, which
  is flaky; a mocked template exercises the same failure path deterministically.

## Alternatives rejected

- **Send after commit (fire-and-forget).** The previous design; loses events on send failure
  or crash (the problem this ADR solves).
- **Kafka transactions.** They make the producer atomic with consumer offsets, not with a
  PostgreSQL commit, so they do not close the dual-write gap.
- **CDC / Debezium.** Requires extra infrastructure and a schema-evolution contract for the
  change stream, disproportionate for catalogue events.
- **DLT in v1.** Adds topic and consumer machinery before the event contract is versioned;
  capped retries plus `last_error` are sufficient to operate the current single topic.
