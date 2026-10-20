-- V7 narrowed products.name to varchar(20) to match the old @Size(min = 3, max = 20)
-- rule. The agreed API limit is now 3-200 characters; widen the column to match.
ALTER TABLE IF EXISTS products
    ALTER COLUMN name TYPE varchar(200);
