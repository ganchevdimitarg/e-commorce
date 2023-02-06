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
    charge_id_stp   text         not null,
    amount          int8         not null,
    currency        text         not null,
    customer_id_stp text         not null,
    receipt_email   text         not null,
    customer_id     varchar(255) not null,
    primary key (charge_id),
    CONSTRAINT fk_charges FOREIGN KEY (customer_id) REFERENCES customers (customer_id)
);

create table cards
(
    card_id          varchar(255) not null unique,
    card_id_stp      text         not null,
    brand            text         not null,
    customer_id_stp  text         not null,
    cvc_check        text         not null,
    exp_month        int8         not null,
    exp_year         int8         not null,
    last_four_digits text         not null,
    customer_id      varchar(255) not null,
    primary key (card_id),
    CONSTRAINT fk_cards FOREIGN KEY (customer_id) REFERENCES customers (customer_id)
);