CREATE TABLE IF NOT EXISTS outbox_events (
    id             uuid PRIMARY KEY,
    aggregate_type varchar(100),
    aggregate_id   varchar(100),
    topic          varchar(200) NOT NULL,
    message_key    varchar(200),
    payload        jsonb        NOT NULL,
    trace_id       varchar(64),
    correlation_id varchar(64),
    status         varchar(20)  NOT NULL DEFAULT 'PENDING',
    published_at   timestamptz  NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    deleted_at     timestamptz  NULL
);

CREATE INDEX IF NOT EXISTS idx_outbox_status_created
    ON outbox_events (status, created_at)
    WHERE deleted_at IS NULL;
