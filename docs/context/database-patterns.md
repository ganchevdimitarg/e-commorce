# Database patterns

## Audit columns — required on every table
```sql
created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
deleted_at  TIMESTAMPTZ NULL
```

## Flyway index example
```sql
-- V4__add_customer_email_index.sql
CREATE UNIQUE INDEX IF NOT EXISTS idx_customers_email
    ON customers (email)
    WHERE deleted_at IS NULL;
```

## Soft-delete query pattern (JPA)
```java
@Query("SELECT o FROM Order o WHERE o.id = :id AND o.deletedAt IS NULL")
Optional<Order> findActiveById(@Param("id") UUID id);
```

## updated_at trigger (PostgreSQL)
```sql
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = now(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_orders_updated_at
    BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
```
