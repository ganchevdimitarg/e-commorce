create table customers
(
    customer_id     varchar(255) not null unique,
    username        text         not null unique,
    customer_name   text         not null,
    customer_id_stp text         not null,
    primary key (customer_id)
);

create table charges
(
    charge_id       varchar(255) not null unique,
    amount          numeric      not null unique,
    currency        text         not null,
    customer_id_stp text         not null,
    receipt_email   text         not null,
    customer_id        text         not null,
    primary key (charge_id),
    CONSTRAINT fk_charges FOREIGN KEY (customer_id) REFERENCES customers (customer_id)
);