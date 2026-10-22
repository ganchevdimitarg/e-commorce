-- One live review per author and product. Historical duplicates fail the migration loudly,
-- matching the V10 pattern: the operator must resolve them before the index can be created.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM comments
        WHERE deleted_at IS NULL
          AND product_id IS NOT NULL
          AND author IS NOT NULL
        GROUP BY product_id, author
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION
            'comments contain duplicate live (product_id, author) pairs; resolve them before applying V19';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_comments_product_author_live
    ON comments (product_id, author)
    WHERE deleted_at IS NULL;
