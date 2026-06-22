ALTER TABLE products
    ALTER COLUMN stock TYPE boolean
    USING (CASE WHEN stock IS NULL THEN NULL ELSE stock::int::boolean END);

ALTER TABLE products
    ALTER COLUMN stock SET DEFAULT false;
