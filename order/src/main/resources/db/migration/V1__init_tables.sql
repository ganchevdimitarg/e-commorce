create table orders
(
    order_id         varchar(36) not null unique,
    order_number     bigint       not null,
    username         text         not null,
    create_on DATETIME(6)  NULL,
    update_on TIMESTAMP    NULL,
    delivery_comment text,

    primary key (order_id)
);

create table items
(
    item_id    varchar(36) not null unique,
    product_id varchar(36) not null,
    quantity   bigint       not null,
    create_on DATETIME(6)  NULL,
    update_on TIMESTAMP    NULL,
    order_id   varchar(36) not null,
    primary key (item_id),
    CONSTRAINT fk_items FOREIGN KEY (order_id) REFERENCES orders (order_id)
);

create table charges
(
    charge_id     varchar(36) not null unique,
    charge_id_stp varchar(255) not null,
    status        varchar(255) not null,
    create_on DATETIME(6)  NULL,
    update_on TIMESTAMP    NULL,
    order_id      varchar(36) not null,
    primary key (charge_id),
    CONSTRAINT fk_items FOREIGN KEY (order_id) REFERENCES orders (order_id)
);