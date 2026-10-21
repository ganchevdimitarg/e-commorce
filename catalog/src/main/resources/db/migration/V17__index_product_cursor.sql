-- Keyset pagination for product search: the cursor predicate and order are on
-- (created_at, id) and only live rows participate. The index is declared DESC to match the
-- default sort; an ascending page uses the same index with a backward scan. The partial
-- predicate mirrors Product's @SQLRestriction so soft-deleted rows never enter the scan.
CREATE INDEX IF NOT EXISTS idx_products_created_at_id
    ON products (created_at DESC, id DESC)
    WHERE deleted_at IS NULL;
