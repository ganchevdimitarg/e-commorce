-- order_number was previously assigned from an in-memory counter seeded at startup, which
-- collides across instances. A DB sequence makes assignment atomic and instance-safe.
-- Seed the sequence above the current max so existing rows keep their numbers.
CREATE SEQUENCE IF NOT EXISTS order_number_seq AS BIGINT START WITH 1 INCREMENT BY 1;

SELECT setval('order_number_seq',
              COALESCE((SELECT MAX(order_number) FROM orders), 0) + 1,
              false);
