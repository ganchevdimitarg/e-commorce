-- Migrate id and foreign-key columns from varchar(255) to native uuid.
-- Existing string ids were generated as UUIDs, so the ::uuid cast is lossless.

-- Drop the FKs that reference the columns being retyped (types must stay compatible).
alter table comments drop constraint if exists product_id;
alter table products drop constraint if exists category_id;

-- Primary keys.
alter table categories alter column id type uuid using id::uuid;
alter table products alter column id type uuid using id::uuid;
alter table comments alter column id type uuid using id::uuid;

-- Foreign-key columns.
alter table products alter column category_id type uuid using category_id::uuid;
alter table comments alter column product_id type uuid using product_id::uuid;

-- Recreate the FKs.
alter table comments
    add constraint product_id foreign key (product_id) references products (id);
alter table products
    add constraint category_id foreign key (category_id) references categories (id);
