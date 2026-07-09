-- Enforce at the database level the provider-id uniqueness the entities declare via
-- @UniqueConstraint. ddl-auto=validate neither creates nor checks those, so without these
-- indexes a duplicated Stripe id could be inserted twice. Partial (WHERE deleted_at IS NULL)
-- so a soft-deleted row never blocks re-use of a provider id.

CREATE UNIQUE INDEX IF NOT EXISTS idx_customers_customer_id_stp
    ON customers (customer_id_stp)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_charges_charge_id_stp
    ON charges (charge_id_stp)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_cards_card_id_stp
    ON cards (card_id_stp)
    WHERE deleted_at IS NULL;
