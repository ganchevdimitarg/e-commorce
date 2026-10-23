-- A relay instance claims a row by setting claimed_until; another instance skips it until
-- the lease expires. A crash after the claim therefore delays delivery, never loses it.
ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS claimed_until TIMESTAMPTZ NULL;
