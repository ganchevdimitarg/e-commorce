package com.ganchevdimitarg.catalog.retention;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Purges one batch of expired rows: soft-deleted comments, products and categories, plus
 * delivered outbox rows.
 *
 * <p>The cutoff is computed by {@link RetentionPurgeScheduler}; the service stays pure so a
 * test or an operator can drive a batch directly. Each call is one transaction, so the
 * scheduler's loop commits every batch independently and a late failure never rolls back
 * earlier batches.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionPurgeService {

    static final String PURGED_METRIC = "catalog.retention.purged";

    private static final String COMMENTS = "comments";
    private static final String PRODUCTS = "products";
    private static final String CATEGORIES = "categories";
    private static final String OUTBOX_EVENTS = "outbox_events";

    private final RetentionPurgeRepository repository;
    private final RetentionProperties properties;
    private final MeterRegistry meterRegistry;

    /**
     * Deletes expired rows in foreign-key order: expired comments, then expired products
     * together with all their comments, then expired categories left without any product
     * row, then delivered outbox rows older than the outbox window. Each selection is
     * limited to {@code catalog.retention.batch-size}; a selected product's comments are all
     * deleted with it, so the total deleted row count can exceed the batch size.
     *
     * @param cutoff       rows soft-deleted strictly before this instant are purged
     * @param outboxCutoff delivered outbox rows sent strictly before this instant are purged;
     *                     unsent and dead-lettered rows are never selected
     * @return the number of comments, products, categories and outbox rows deleted by this
     * batch
     */
    @Transactional
    public PurgeResult purgeBatch(Instant cutoff, Instant outboxCutoff) {
        int expiredComments = repository.deleteExpiredComments(cutoff, properties.batchSize());
        List<UUID> expiredProductIds = repository.findExpiredProductIds(cutoff, properties.batchSize());
        int productComments = expiredProductIds.isEmpty()
                ? 0
                : repository.deleteCommentsForProducts(expiredProductIds);
        int products = expiredProductIds.isEmpty()
                ? 0
                : repository.deleteProducts(expiredProductIds);
        int categories = repository.deleteEmptyExpiredCategories(cutoff, properties.batchSize());
        int outboxEvents = repository.deleteSentOutboxEvents(outboxCutoff, properties.batchSize());

        PurgeResult result = new PurgeResult(expiredComments + productComments, products,
                categories, outboxEvents);
        incrementCounters(result);
        logBatch(result, cutoff, outboxCutoff);
        return result;
    }

    private void incrementCounters(PurgeResult result) {
        meterRegistry.counter(PURGED_METRIC, "entity", COMMENTS).increment(result.comments());
        meterRegistry.counter(PURGED_METRIC, "entity", PRODUCTS).increment(result.products());
        meterRegistry.counter(PURGED_METRIC, "entity", CATEGORIES).increment(result.categories());
        meterRegistry.counter(PURGED_METRIC, "entity", OUTBOX_EVENTS).increment(result.outboxEvents());
    }

    private void logBatch(PurgeResult result, Instant cutoff, Instant outboxCutoff) {
        if (result.isEmpty()) {
            log.debug("Retention purge removed nothing before cutoff {} (outbox cutoff {})",
                    cutoff, outboxCutoff);
            return;
        }
        log.info("Retention purge removed comments={} products={} categories={} outboxEvents={} "
                        + "before cutoff {} (outbox cutoff {})",
                result.comments(), result.products(), result.categories(), result.outboxEvents(),
                cutoff, outboxCutoff);
    }

    /**
     * Rows deleted by one batch. The scheduler stops looping once a batch removes nothing.
     */
    public record PurgeResult(int comments, int products, int categories, int outboxEvents) {

        public boolean isEmpty() {
            return comments == 0 && products == 0 && categories == 0 && outboxEvents == 0;
        }
    }
}
