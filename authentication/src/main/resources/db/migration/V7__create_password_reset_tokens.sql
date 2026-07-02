CREATE TABLE IF NOT EXISTS password_reset_tokens
(
    id          uuid        PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES user_credentials (id),
    token_hash  varchar(64) NOT NULL,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    deleted_at  timestamptz NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_prt_token_hash
    ON password_reset_tokens (token_hash)
    WHERE used_at IS NULL AND deleted_at IS NULL;
