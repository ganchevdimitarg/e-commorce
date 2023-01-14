create table payments
(
    id          varchar(255) not null unique,
    card_number text         not null unique,
    card_name   text         not null,
    card_cvc    text         not null,
    card_month  text         not null,
    card_year   text         not null,
    primary key (id)
);

create table customers
(
    id            varchar(255) not null unique,
    email         text         not null unique,
    customer_name text         not null,
    customer_id   text         not null,
    primary key (id)
);

create table charges
(
    id            varchar(255) not null unique,
    amount        numeric      not null unique,
    currency      text         not null,
    customer_id   text         not null,
    receipt_email text         not null,
    customer      text         not null references customers (id),
    primary key (id)
);