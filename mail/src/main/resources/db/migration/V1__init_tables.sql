CREATE TABLE mail
(
    id         VARCHAR(255) NOT NULL,
    recipient  TEXT,
    user_id    VARCHAR(255),
    subject    VARCHAR(200),
    msg_body   TEXT,
    attachment VARCHAR(255),
    created_on TIMESTAMP WITHOUT TIME ZONE,
    type       INTEGER,
    fields     VARCHAR(255),
    CONSTRAINT pk_mail PRIMARY KEY (id)
);

CREATE TABLE mail_list
(
    id                       VARCHAR(255) NOT NULL,
    user_id                  VARCHAR(255),
    is_user_active           BOOLEAN      NOT NULL,
    signed_for_announcements BOOLEAN      NOT NULL,
    signed_for_promotions    BOOLEAN      NOT NULL,
    signed_for_notifications BOOLEAN      NOT NULL,
    sent_mails_for_user      INTEGER,
    CONSTRAINT pk_mail_list PRIMARY KEY (id)
);

ALTER TABLE mail
    ADD CONSTRAINT uc_mail_id UNIQUE (id);

ALTER TABLE mail_list
    ADD CONSTRAINT uc_mail_list_id UNIQUE (id);
