CREATE TABLE categories
(
    id        VARCHAR(36)  NOT NULL,
    version   BIGINT       NULL,
    name      VARCHAR(200) NOT NULL,
    create_on DATETIME(6)  NULL,
    update_on TIMESTAMP    NULL,
    CONSTRAINT pk_categories PRIMARY KEY (id)
);

CREATE TABLE comments
(
    id         VARCHAR(36)      NOT NULL,
    version    BIGINT           NULL,
    title      TEXT             NULL,
    text       TEXT             NULL,
    star       DOUBLE PRECISION NOT NULL,
    author     VARCHAR(200)     NULL,
    create_on  DATETIME(6)      NULL,
    update_on  TIMESTAMP        NULL,
    product_id VARCHAR(36)      NULL,
    CONSTRAINT pk_comments PRIMARY KEY (id)
);

CREATE TABLE products
(
    id              VARCHAR(36)  NOT NULL,
    version         BIGINT       NULL,
    name            VARCHAR(300)  NOT NULL,
    description     VARCHAR(150)  NOT NULL,
    price           DECIMAL      NOT NULL,
    stock           BIT(1)       NULL,
    characteristics VARCHAR(255) NULL,
    create_on       DATETIME(6)  NULL,
    update_on       TIMESTAMP    NULL,
    category_id     VARCHAR(36)  NULL,
    CONSTRAINT pk_products PRIMARY KEY (id)
);





