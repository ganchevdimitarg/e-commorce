package com.ganchevdimitarg.catalog.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.catalog.domain.OutboxEvent;
import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Writes product events to the transactional outbox.
 *
 * <p>Every method is called from inside the caller's write transaction, so the event row
 * commits or rolls back together with the product row. Delivery to Kafka is
 * {@link OutboxRelay}'s job, not the writer's.
 */
@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

    private static final String AGGREGATE_TYPE = "Product";

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public void writeCreated(Product product) {
        write(product, ProductTopics.PRODUCT_CREATED, "created",
                new ProductEvent.ProductCreated(newEventId(), productId(product), product.getName(),
                        product.getPrice(), product.isInStock(), categoryName(product),
                        product.getVersion(), ProductEvent.SCHEMA_VERSION, Instant.now()));
    }

    public void writeUpdated(Product product) {
        write(product, ProductTopics.PRODUCT_UPDATED, "updated",
                new ProductEvent.ProductUpdated(newEventId(), productId(product), product.getName(),
                        product.getPrice(), product.isInStock(), categoryName(product),
                        product.getVersion(), ProductEvent.SCHEMA_VERSION, Instant.now()));
    }

    public void writeDeleted(Product product) {
        write(product, ProductTopics.PRODUCT_DELETED, "deleted",
                new ProductEvent.ProductDeleted(newEventId(), productId(product), product.getName(),
                        product.getPrice(), product.isInStock(), categoryName(product),
                        product.getVersion(), ProductEvent.SCHEMA_VERSION, Instant.now()));
    }

    /**
     * Records a category move for one product.
     *
     * <p>The move is applied by a bulk update that bypasses the persistence context, so the
     * new category name comes from the caller and the row version is the pre-update version
     * plus one.
     *
     * @param previousCategoryName the category the product left
     * @param newCategoryName      the category the product moved to
     */
    public void writeCategoryChanged(Product product, String previousCategoryName, String newCategoryName) {
        write(product, ProductTopics.PRODUCT_CATEGORY_CHANGED, "category-changed",
                new ProductEvent.ProductCategoryChanged(newEventId(), productId(product), product.getName(),
                        product.getPrice(), product.isInStock(), newCategoryName, previousCategoryName,
                        product.getVersion() + 1, ProductEvent.SCHEMA_VERSION, Instant.now()));
    }

    private void write(Product product, String topic, String eventType, ProductEvent event) {
        Instant now = Instant.now();
        String productId = productId(product);

        OutboxEvent row = new OutboxEvent();
        row.setAggregateType(AGGREGATE_TYPE);
        row.setAggregateId(productId);
        row.setEventType(eventType);
        row.setTopic(topic);
        row.setMessageKey(productId);
        row.setPayload(serialise(event));
        row.setHeaders(Map.of(
                "eventType", eventType,
                "correlationId", event.eventId(),
                "traceId", traceId()));
        row.setAttempts(0);
        row.setCreatedAt(now);
        row.setNextAttemptAt(now);
        row.setSentAt(null);

        repository.save(row);
    }

    /**
     * A serialisation failure must fail the business transaction: silently dropping the
     * event would leave the product change committed with no outbox row to relay.
     */
    private String serialise(ProductEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise product event " + event.eventId(), e);
        }
    }

    private static String productId(Product product) {
        return product.getId().toString();
    }

    /**
     * {@code products.category_id} is nullable, so a legacy product may have no category.
     */
    private static String categoryName(Product product) {
        return product.getCategory() == null ? null : product.getCategory().getName();
    }

    private static String newEventId() {
        return UUID.randomUUID().toString();
    }

    private static String traceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : "";
    }
}
