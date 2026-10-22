-- Name search is a case-insensitive contains: lower(name) LIKE '%q%'. A B-tree cannot
-- serve a leading-wildcard LIKE; a trigram GIN index on the same expression can. The
-- partial predicate mirrors Product's @SQLRestriction("deleted_at IS NULL") so the index
-- matches the query Hibernate emits.
--
-- pg_trgm is trusted in PostgreSQL 13+, but a restricted database user may still need a
-- DBA to install the extension once before this migration runs.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_products_name_trgm
    ON products USING gin (lower(name) gin_trgm_ops)
    WHERE deleted_at IS NULL;
