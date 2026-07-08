-- Immutable audit trail of every order status transition.
CREATE TABLE IF NOT EXISTS order_status_history
(
    history_id  VARCHAR(255) NOT NULL,
    order_id    VARCHAR(255) NOT NULL,
    from_status VARCHAR(32),
    to_status   VARCHAR(32)  NOT NULL,
    changed_by  VARCHAR(255) NOT NULL,
    reason      TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ  NULL,
    PRIMARY KEY (history_id),
    CONSTRAINT fk_order_status_history_order
        FOREIGN KEY (order_id) REFERENCES orders (order_id)
);

CREATE INDEX IF NOT EXISTS idx_order_status_history_order
    ON order_status_history (order_id);

CREATE TRIGGER trg_order_status_history_updated_at
    BEFORE UPDATE ON order_status_history
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
