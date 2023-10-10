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
    card_id          VARCHAR(36)  NOT NULL,
    card_id_stp      VARCHAR(255) NOT NULL,
    brand            VARCHAR(255) NOT NULL,
    customer_id_stp  VARCHAR(255) NOT NULL,
    card_number      VARCHAR(255),
    cvc_check        VARCHAR(255) NOT NULL,
    exp_month        BIGINT       NOT NULL,
    exp_year         BIGINT       NOT NULL,
    last_four_digits VARCHAR(255) NOT NULL,
    customer_id      VARCHAR(36)  NOT NULL,
    primary key (card_id),
    CONSTRAINT fk_cards FOREIGN KEY (customer_id) REFERENCES customers (customer_id)
);