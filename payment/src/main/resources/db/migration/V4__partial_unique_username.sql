-- Reconcile soft-delete with the username uniqueness rule. The original hard UNIQUE
-- constraint keeps a soft-deleted customer's username reserved forever, so the user can
-- never re-register. Replace it with a partial unique index scoped to live rows only.

ALTER TABLE customers DROP CONSTRAINT IF EXISTS customers_username_key;

CREATE UNIQUE INDEX IF NOT EXISTS idx_customers_username
    ON customers (username)
    WHERE deleted_at IS NULL;
