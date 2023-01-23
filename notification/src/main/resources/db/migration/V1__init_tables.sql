CREATE TABLE notification
(
    id         VARCHAR(255) NOT NULL,
    user_id    VARCHAR(255),
    subject    VARCHAR(200),
    msg_body   TEXT,
    link       VARCHAR(255),
    created_on TIMESTAMP WITHOUT TIME ZONE,
    is_viewed  BOOLEAN      NOT NULL,
    CONSTRAINT pk_notification PRIMARY KEY (id)
);

ALTER TABLE notification
    ADD CONSTRAINT uc_notification_id UNIQUE (id);
