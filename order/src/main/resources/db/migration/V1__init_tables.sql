create table orders
(
    id               varchar(255) not null unique,
    order_number     bigint       not null unique,
    username         text         not null,
    product_id       varchar(255) not null,
    product_name     text         not null,
    quantity         bigint       not null,
    created_on       timestamp,
    delivery_comment text,
    primary key (id)
);