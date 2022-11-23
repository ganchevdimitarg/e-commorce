create table categories
(
    id   varchar(255) not null unique,
    name varchar(200) not null,
    primary key (id)
);

create table comments
(
    id         varchar(255)     not null unique,
    author     varchar(200),
    star       double precision not null,
    text       TEXT,
    title      TEXT,
    product_id varchar(255),
    primary key (id)
);

create table products
(
    id              varchar(255)   not null unique,
    name            varchar(200)   not null,
    characteristics text,
    description     text           not null,
    stock           bit,
    price           decimal(19, 2) not null,
    category_id     varchar(255),
    primary key (id)
);