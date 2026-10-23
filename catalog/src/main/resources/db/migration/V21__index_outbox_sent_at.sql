-- Delivered rows are purged by the retention job after catalog.outbox.retention (7 days by
-- default). Dead-lettered rows keep sent_at NULL and are never selected; unsent rows are
-- untouched. The existing idx_outbox_events_unsent_live covers the claim query, not this one.
CREATE INDEX IF NOT EXISTS idx_outbox_events_sent_at
    ON outbox_events (sent_at)
    WHERE sent_at IS NOT NULL;
