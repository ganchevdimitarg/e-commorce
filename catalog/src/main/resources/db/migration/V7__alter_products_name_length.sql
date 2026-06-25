-- Narrow products.name to match the @Size(min=3,max=20) business rule.
-- Existing rows were inserted under the same @Size cap, so no truncation occurs.
ALTER TABLE IF EXISTS products
    ALTER COLUMN name TYPE varchar(20);
