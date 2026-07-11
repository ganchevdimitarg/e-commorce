-- Optimistic locking for the order aggregate (review Top-10 #6): catalog's V6 pattern.
ALTER TABLE orders               ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE items                ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE charges              ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE order_status_history ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
