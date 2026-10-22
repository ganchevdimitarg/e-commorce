-- Destructive perf seed for the standalone stack. Resets the catalogue tables.
-- 10,000 products; products 1-3 are the newest rows and have 2,000 live comments each,
-- so they land on page 1 of the search and cursor queries.
TRUNCATE TABLE comments, products, categories CASCADE;

INSERT INTO categories (id, name, created_at, updated_at, version)
VALUES ('00000000-0000-0000-0000-000000000001', 'Perf', now(), now(), 0);

INSERT INTO products (id, name, description, price, stock, characteristics,
                      category_id, created_at, updated_at, version, rating_count, avg_stars)
SELECT ('00000000-0000-0000-0000-' || lpad(g::text, 12, '0'))::uuid,
       'Phone ' || g,
       'Performance seed product ' || g || ' with an ordinary description.',
       (g % 1000) + 0.99,
       true,
       '{"colour":"black","warranty":"2 years"}',
       '00000000-0000-0000-0000-000000000001'::uuid,
       now() - make_interval(mins => g),
       now() - make_interval(mins => g),
       0,
       CASE WHEN g <= 3 THEN 2000 ELSE 0 END,
       CASE WHEN g <= 3 THEN 3.0 ELSE 0.0 END
FROM generate_series(1, 10000) AS g;

INSERT INTO comments (id, author, star, text, title, product_id,
                      created_at, updated_at, version)
SELECT ('00000000-0000-0000-0001-' || lpad((p * 100000 + n)::text, 12, '0'))::uuid,
       'perf-user-' || p || '-' || n,
       1 + (n % 5),
       'Seeded review ' || n || ' for product ' || p || ' with ordinary text.',
       'Review ' || n,
       ('00000000-0000-0000-0000-' || lpad(p::text, 12, '0'))::uuid,
       now() - make_interval(mins => n),
       now() - make_interval(mins => n),
       0
FROM generate_series(1, 3) AS p
CROSS JOIN generate_series(1, 2000) AS n;

ANALYZE categories;
ANALYZE products;
ANALYZE comments;
