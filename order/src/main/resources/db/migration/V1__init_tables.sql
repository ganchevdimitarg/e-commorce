CREATE TABLE charges
(
    id            BIGSERIAL    NOT NULL,
    charge_id_stp VARCHAR(255) NOT NULL,
    status        VARCHAR(255) NOT NULL,
    created_on    TIMESTAMP WITHOUT TIME ZONE,
    updated_on    TIMESTAMP WITHOUT TIME ZONE,
    order_id      BIGINT       NOT NULL,
    CONSTRAINT pk_charges PRIMARY KEY (id)
);

CREATE TABLE items
(
    id         BIGSERIAL    NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    quantity   BIGINT       NOT NULL,
    created_on TIMESTAMP WITHOUT TIME ZONE,
    updated_on TIMESTAMP WITHOUT TIME ZONE,
    order_id   BIGINT       NOT NULL,
    CONSTRAINT pk_items PRIMARY KEY (id)
);

CREATE TABLE orders
(
    id               BIGSERIAL    NOT NULL,
    order_number     BIGINT       NOT NULL,
    username         VARCHAR(255) NOT NULL,
    delivery_comment VARCHAR(255),
    created_on       TIMESTAMP WITHOUT TIME ZONE,
    updated_on       TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_orders PRIMARY KEY (id)
);

ALTER TABLE charges
    ADD CONSTRAINT charge_id_stp UNIQUE (charge_id_stp);

ALTER TABLE orders
    ADD CONSTRAINT order_number UNIQUE (order_number);


CREATE INDEX charge_id_stp_index ON charges (charge_id_stp);

CREATE INDEX order_number_index ON orders (order_number);

CREATE INDEX product_id_index ON items (product_id);