alter table categories
    add constraint category_name unique (name);
alter table products
    add constraint product_name unique (name);
alter table comments
    add constraint product_id
        foreign key (product_id)
            references products (id);
alter table products
    add constraint category_id
        foreign key (category_id)
            references categories (id);
