create table orders
(
    order_id         varchar(255) not null unique,
    order_number     bigint       not null,
    username         text         not null,
    created_on       timestamp,
    delivery_comment text,
    primary key (order_id)
);

create table items
(
    item_id    varchar(255) not null unique,
    product_id varchar(255) not null,
    quantity   bigint       not null,
    order_id   varchar(255) not null,
    primary key (item_id),
    CONSTRAINT fk_items FOREIGN KEY (order_id) REFERENCES orders (order_id)
);

create table charges
(
    charge_id     varchar(255) not null unique,
    charge_id_stp varchar(255) not null,
    status        varchar(255) not null,
    order_id      varchar(255) not null,
    primary key (charge_id),
    CONSTRAINT fk_items FOREIGN KEY (order_id) REFERENCES orders (order_id)
);