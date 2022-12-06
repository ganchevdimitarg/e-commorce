create table categories
(
    id   varchar(255) not null,
    name varchar(200) not null,
    primary key (id)
);

create table comments
(
    id         varchar(255)     not null,
    author     varchar(200),
    star       double precision not null,
    text       TEXT,
    title      TEXT,
    product_id varchar(255),
    primary key (id)
);

create table products
(
    id              varchar(255)   not null,
    characteristics TEXT,
    description     TEXT           not null,
    stock           bit,
    name            varchar(200)   not null,
    price           decimal(19, 2) not null,
    category_id     varchar(255),
    primary key (id)
);