create table notification
(
    id   varchar(255)   not null,
    recipient TEXT      not null,
    subject TEXT        not null,
    msg_body TEXT        not null,
    attachment varchar(255),
    primary key (id)
);