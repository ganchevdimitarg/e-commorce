-- The retention purge scans soft-deleted rows by age. Partial indexes keep the scan
-- proportional to the soft-deleted backlog rather than the whole table, and cost nothing
-- for live rows. comments(product_id) already exists in V11 and is used by the product
-- cascade delete.
CREATE INDEX IF NOT EXISTS idx_products_deleted_at
    ON products (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_comments_deleted_at
    ON comments (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_categories_deleted_at
    ON categories (deleted_at) WHERE deleted_at IS NOT NULL;
