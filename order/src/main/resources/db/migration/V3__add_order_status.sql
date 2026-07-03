-- Order lifecycle status. Existing rows are backfilled to PLACED by the DEFAULT.
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS status VARCHAR(32) NOT NULL DEFAULT 'PLACED';
