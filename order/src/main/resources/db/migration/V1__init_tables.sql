CREATE TABLE charges
(
    charge_id     VARCHAR(36)  NOT NULL,
    charge_id_stp VARCHAR(255) NOT NULL,
    status        VARCHAR(255) NOT NULL,
    create_on     TIMESTAMP WITHOUT TIME ZONE,
    update_on     TIMESTAMP WITHOUT TIME ZONE,
    order_id      VARCHAR(36),
    CONSTRAINT pk_charges PRIMARY KEY (charge_id)
);

CREATE TABLE items
(
    item_id    VARCHAR(36)  NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    quantity   BIGINT       NOT NULL,
    create_on  TIMESTAMP WITHOUT TIME ZONE,
    update_on  TIMESTAMP WITHOUT TIME ZONE,
    order_id   VARCHAR(36),
    CONSTRAINT pk_items PRIMARY KEY (item_id)
);

CREATE TABLE orders
(
    order_id         VARCHAR(36)  NOT NULL,
    order_number     BIGINT       NOT NULL,
    username         VARCHAR(255) NOT NULL,
    created_on       TIMESTAMP WITHOUT TIME ZONE,
    delivery_comment VARCHAR(255),
    create_on        TIMESTAMP WITHOUT TIME ZONE,
    update_on        TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_orders PRIMARY KEY (order_id)
);

ALTER TABLE charges
    ADD CONSTRAINT charge_id_stp UNIQUE (charge_id_stp);

ALTER TABLE orders
    ADD CONSTRAINT order_number UNIQUE (order_number);

ALTER TABLE items
    ADD CONSTRAINT item_id UNIQUE (item_id);

CREATE INDEX charge_id_stp_index ON charges (charge_id_stp);

CREATE INDEX order_number_index ON orders (order_number);

CREATE INDEX product_id_index ON items (product_id);

ALTER TABLE charges
    ADD CONSTRAINT FK_CHARGES_ON_ORDER FOREIGN KEY (order_id) REFERENCES orders (order_id);

ALTER TABLE items
    ADD CONSTRAINT FK_ITEMS_ON_ORDER FOREIGN KEY (order_id) REFERENCES orders (order_id);