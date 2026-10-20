package com.ganchevdimitarg.catalog.event;

/**
 * Kafka topics written by catalog's product events.
 *
 * <p>Topics are declared here rather than on the writer so the relay, the {@code NewTopic}
 * configuration and the tests share a single source of truth.
 */
public final class ProductTopics {

    public static final String PRODUCT_CREATED = "catalog.product.created";
    public static final String PRODUCT_UPDATED = "catalog.product.updated";
    public static final String PRODUCT_DELETED = "catalog.product.deleted";
    public static final String PRODUCT_CATEGORY_CHANGED = "catalog.product.category-changed";

    private ProductTopics() {
    }
}
