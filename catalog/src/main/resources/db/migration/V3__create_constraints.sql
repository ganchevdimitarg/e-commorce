ALTER TABLE categories
    ADD CONSTRAINT category_name UNIQUE (name);

ALTER TABLE comments
    ADD CONSTRAINT FK_COMMENTS_ON_PRODUCT FOREIGN KEY (product_id) REFERENCES products (id);

ALTER TABLE products
    ADD CONSTRAINT product_name UNIQUE (name);

ALTER TABLE products
    ADD CONSTRAINT FK_PRODUCTS_ON_CATEGORY FOREIGN KEY (category_id) REFERENCES categories (id);