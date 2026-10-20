package com.ganchevdimitarg.catalog.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Product events written to the transactional outbox.
 *
 * <p>{@link #SCHEMA_VERSION} is carried by every payload so consumers can evolve their
 * deserialisation without guessing the shape. It is a record component rather than a
 * default method so Jackson serialises it as a payload field.
 */
public sealed interface ProductEvent
        permits ProductEvent.ProductCreated, ProductEvent.ProductUpdated,
                ProductEvent.ProductDeleted, ProductEvent.ProductCategoryChanged {

    int SCHEMA_VERSION = 1;

    String eventId();

    String productId();

    String productName();

    BigDecimal price();

    boolean inStock();

    String categoryName();

    long version();

    int schemaVersion();

    Instant occurredAt();

    record ProductCreated(String eventId, String productId, String productName,
                          BigDecimal price, boolean inStock, String categoryName,
                          long version, int schemaVersion, Instant occurredAt) implements ProductEvent {}

    record ProductUpdated(String eventId, String productId, String productName,
                          BigDecimal price, boolean inStock, String categoryName,
                          long version, int schemaVersion, Instant occurredAt) implements ProductEvent {}

    record ProductDeleted(String eventId, String productId, String productName,
                          BigDecimal price, boolean inStock, String categoryName,
                          long version, int schemaVersion, Instant occurredAt) implements ProductEvent {}

    /**
     * Emitted when products are moved between categories. {@code categoryName} is the new
     * category and {@code previousCategoryName} the one the product left.
     */
    record ProductCategoryChanged(String eventId, String productId, String productName,
                                  BigDecimal price, boolean inStock, String categoryName,
                                  String previousCategoryName, long version,
                                  int schemaVersion, Instant occurredAt) implements ProductEvent {}
}
