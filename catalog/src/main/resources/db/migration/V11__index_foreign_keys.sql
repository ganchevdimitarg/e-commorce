-- PostgreSQL does not index foreign-key columns automatically. Both columns are read on
-- category-scoped product queries and on the FK existence checks used by deletes and moves.
CREATE INDEX IF NOT EXISTS idx_products_category_id ON products (category_id);
CREATE INDEX IF NOT EXISTS idx_comments_product_id ON comments (product_id);
