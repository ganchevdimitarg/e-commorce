-- Name uniqueness must apply only to live rows: full-table unique constraints block
-- re-creating a name after its previous row was soft-deleted.
-- Drop every unique constraint on categories.name / products.name, whatever it is called.
DO $$
DECLARE r record;
BEGIN
    FOR r IN
        SELECT t.relname AS tbl, c.conname AS cons
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        WHERE c.contype = 'u'
          AND t.relname IN ('categories', 'products')
          AND c.conkey = ARRAY[
              (SELECT a.attnum FROM pg_attribute a
               WHERE a.attrelid = c.conrelid AND a.attname = 'name')
          ]::smallint[]
    LOOP
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT %I', r.tbl, r.cons);
    END LOOP;
END $$;

-- The plain name indexes are superseded by the partial unique indexes below.
DROP INDEX IF EXISTS category_index;
DROP INDEX IF EXISTS product_index;

CREATE UNIQUE INDEX IF NOT EXISTS ux_categories_name_active
    ON categories (name) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_products_name_active
    ON products (name) WHERE deleted_at IS NULL;

-- Fail loudly if a full-table unique constraint survived.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid
        WHERE c.contype = 'u' AND t.relname IN ('categories', 'products')
          AND c.conkey = ARRAY[
              (SELECT a.attnum FROM pg_attribute a
               WHERE a.attrelid = c.conrelid AND a.attname = 'name')
          ]::smallint[]
    ) THEN
        RAISE EXCEPTION 'A unique constraint on name still exists';
    END IF;
END $$;
