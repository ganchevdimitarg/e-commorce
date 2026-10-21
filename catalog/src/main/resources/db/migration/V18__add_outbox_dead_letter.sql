-- Producer-side dead-lettering for poisoned outbox events. A row is parked here when the
-- failure can never succeed on retry (serialisation, oversized or invalid record); broker
-- and network failures keep retrying with the relay's backoff instead.
ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS dead_lettered_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS dead_letter_reason TEXT NULL;

CREATE INDEX IF NOT EXISTS idx_outbox_events_unsent_live
    ON outbox_events (created_at)
    WHERE sent_at IS NULL AND dead_lettered_at IS NULL;
