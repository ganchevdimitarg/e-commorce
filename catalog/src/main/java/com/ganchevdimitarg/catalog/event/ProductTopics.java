package com.ganchevdimitarg.catalog.event;

/**
 * Kafka topics written by catalog's product events.
 *
 * <p>Topics are declared here rather than on the writer so the relay and the future
 * {@code NewTopic} configuration (TODO 4.5) share a single source of truth.
 */
public final class ProductTopics {

    public static final String PRODUCT_CREATED = "catalog.product.created";
    public static final String PRODUCT_UPDATED = "catalog.product.updated";
    public static final String PRODUCT_DELETED = "catalog.product.deleted";
    // Task 8 adds PRODUCT_CATEGORY_CHANGED

    private ProductTopics() {
    }
}
