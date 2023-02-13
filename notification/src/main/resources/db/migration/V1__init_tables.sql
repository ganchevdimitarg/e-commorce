create table notifications
(
    id         varchar(255) not null unique,
    recipient  text         not null,
    subject    text         not null,
    msg_body   text         not null,
    attachment varchar(255),
    created_on timestamp,
    primary key (id)
);