# Outbox dead-letter runbook

Rows the relay can never deliver are parked with `dead_lettered_at` set. They are excluded
from claims and are never purged by the retention job. This runbook lists them, finds the
cause and requeues a fixed row.

## Alert

Page when `catalog.outbox.dead_lettered` increases for more than a few minutes, or when
`catalog.outbox.pending` grows without `catalog.outbox.published` keeping pace.

Micrometer exports the counters as `catalog_outbox_dead_lettered_total` and
`catalog_outbox_published_total`, and the gauge as `catalog_outbox_pending`. An example
PromQL alert:

    sum(rate(catalog_outbox_dead_lettered_total[5m])) > 0

## List dead letters

    select id, aggregate_id, event_type, topic, attempts,
           last_error, dead_lettered_at, dead_letter_reason
    from outbox_events
    where dead_lettered_at is not null
    order by dead_lettered_at desc;

## Inspect one row

    select id, aggregate_id, topic, message_key, payload, headers,
           attempts, last_error, dead_letter_reason
    from outbox_events
    where id = '<id>';

`payload` is the exact JSON the relay would publish. `last_error` and
`dead_letter_reason` normally carry the producer's serialisation, size or record-validity
message; the cause is almost always a payload the current producer or broker cannot accept
(a schema/field mismatch, an oversized record, or an invalid topic).

## Fix, then requeue

Fix the cause first. Requeuing an unchanged poison payload only parks the row again.

    update outbox_events
    set dead_lettered_at = null,
        dead_letter_reason = null,
        attempts = 0,
        next_attempt_at = now(),
        claimed_until = null
    where id = '<id>'
      and dead_lettered_at is not null;

Run one row at a time; do not bulk-requeue without inspected causes. A successful requeue
shows up as `catalog.outbox.published` increasing and the row gaining `sent_at`; the
row is then eligible for the outbox retention purge.

## Notes

- Never delete a dead-lettered row as a shortcut: it is the only record of an event the
  business transaction committed.
- Clearing `claimed_until` matters even for parked rows: a crashed relay may have claimed
  the row before it was dead-lettered, and the lease would otherwise delay the retry.
- The retention purge removes only `sent_at IS NOT NULL` rows, so dead-lettered rows stay
  until an operator requeues or archives them.
