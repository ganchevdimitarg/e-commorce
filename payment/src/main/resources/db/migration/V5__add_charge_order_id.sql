-- Link a charge to the order it settles, so PaymentCompletedEvent can carry the orderId
-- the order saga correlates on. Nullable at the DB level (never breaks ddl-auto=validate,
-- and any pre-existing rows stay valid); presence is enforced by @NotBlank on the command.

ALTER TABLE charges ADD COLUMN IF NOT EXISTS order_id VARCHAR(255);
