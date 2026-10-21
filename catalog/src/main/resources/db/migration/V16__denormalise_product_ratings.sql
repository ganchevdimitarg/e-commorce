-- Denormalised rating: counts and average are derived from live comments only.
-- Comment create/soft-delete recalculates both values inside the comment transaction, so
-- reads (product responses, avg-stars) no longer aggregate the comments table.
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS rating_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS avg_stars DOUBLE PRECISION NOT NULL DEFAULT 0;

-- Backfill existing rows from their live comments. Products with no live comments keep
-- the defaults; soft-deleted comments are excluded by the deleted_at filter, which mirrors
-- the Comment entity's @SQLRestriction.
UPDATE products p
SET rating_count = s.cnt,
    avg_stars = COALESCE(s.avg, 0)
FROM (
    SELECT product_id, count(*) AS cnt, avg(star) AS avg
    FROM comments
    WHERE deleted_at IS NULL
    GROUP BY product_id
) s
WHERE p.id = s.product_id;
