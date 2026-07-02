CREATE TABLE IF NOT EXISTS user_credentials
(
    id            uuid         PRIMARY KEY,
    email         varchar(255) NOT NULL,
    password_hash varchar(255) NOT NULL,
    enabled       boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    deleted_at    timestamptz  NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_user_credentials_email
    ON user_credentials (email)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS user_credential_roles
(
    credential_id uuid        NOT NULL REFERENCES user_credentials (id),
    role          varchar(64) NOT NULL,
    PRIMARY KEY (credential_id, role)
);
