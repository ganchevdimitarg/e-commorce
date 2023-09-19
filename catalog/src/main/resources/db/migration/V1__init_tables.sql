drop table if exists catalog.categories;
CREATE TABLE categories
(
    id        BINARY(16)   NOT NULL,
    version   BIGINT       NULL,
    name      VARCHAR(200) NOT NULL,
    create_on datetime     NULL,
    update_on datetime     NULL,
    CONSTRAINT pk_categories PRIMARY KEY (id)
);

drop table if exists catalog.comments;
CREATE TABLE comments
(
    id         BINARY(16)   NOT NULL,
    version    BIGINT       NULL,
    title      TEXT         NULL,
    text       TEXT         NULL,
    star       DOUBLE       NOT NULL,
    author     VARCHAR(200) NULL,
    create_on  datetime     NULL,
    update_on  datetime     NULL,
    product_id BINARY(16)   NULL,
    CONSTRAINT pk_comments PRIMARY KEY (id)
);

drop table if exists catalog.products;
CREATE TABLE products
(
    id              BINARY(16)   NOT NULL,
    version         BIGINT       NULL,
    name            VARCHAR(300) NOT NULL,
    `description`   VARCHAR(150) NOT NULL,
    price           DECIMAL      NOT NULL,
    stock           BIT(1)       NULL,
    characteristics VARCHAR(255) NULL,
    create_on       datetime     NULL,
    update_on       datetime     NULL,
    category_id     BINARY(16)   NULL,
    CONSTRAINT pk_products PRIMARY KEY (id)
);





