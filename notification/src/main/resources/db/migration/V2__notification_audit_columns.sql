-- Bring the notification table up to the repo audit-column contract:
-- created_at / updated_at / deleted_at (TIMESTAMPTZ) with an updated_at trigger.

ALTER TABLE notification RENAME COLUMN created_on TO created_at;
ALTER TABLE notification ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';
UPDATE notification SET created_at = now() WHERE created_at IS NULL;
ALTER TABLE notification ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE notification ALTER COLUMN created_at SET DEFAULT now();

ALTER TABLE notification ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE notification ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ NULL;

CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_notification_updated_at ON notification;
CREATE TRIGGER trg_notification_updated_at
    BEFORE UPDATE ON notification
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
