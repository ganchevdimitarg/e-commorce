package com.ganchevdimitarg.catalog.retention;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the purge boundaries against the real schema.
 *
 * <p>Rows are seeded with native SQL because {@code @SQLRestriction} hides soft-deleted
 * entities from JPA, and asserted the same way. The class-level transaction rolls every
 * seeded row back, so the shared Postgres container is left untouched.
 */
@Tag("integration")
@Transactional
class RetentionPurgeIT extends AbstractIntegrationTest {

    @Autowired
    RetentionPurgeService service;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void should_purgeOnlyExpiredSoftDeletedRows_when_batchRuns() {
        UUID oldCategory = insertCategory("old-cat-" + suffix(), 100);
        UUID liveCategory = insertCategory("live-cat-" + suffix(), 0);

        UUID oldProduct = insertProduct("old-product-" + suffix(), liveCategory, 100);
        UUID oldProductLiveComment = insertComment(oldProduct, 0);
        UUID oldProductOldComment = insertComment(oldProduct, 100);

        UUID newProduct = insertProduct("new-product-" + suffix(), liveCategory, 10);
        UUID newProductLiveComment = insertComment(newProduct, 0);
        UUID newProductOldComment = insertComment(newProduct, 100);

        UUID liveProduct = insertProduct("live-product-" + suffix(), liveCategory, 0);
        UUID liveComment = insertComment(liveProduct, 0);

        // An expired category that still holds a live product must survive the purge.
        UUID expiredCategoryWithProduct = insertCategory("busy-cat-" + suffix(), 100);
        UUID retainedProduct = insertProduct("retained-product-" + suffix(), expiredCategoryWithProduct, 0);

        // An expired category whose only product is also expired must go in the same batch:
        // products are deleted before the empty-category pass runs.
        UUID expiredCategoryWithExpiredProduct = insertCategory("cascade-cat-" + suffix(), 100);
        UUID expiredProductInExpiredCategory =
                insertProduct("cascade-product-" + suffix(), expiredCategoryWithExpiredProduct, 100);

        RetentionPurgeService.PurgeResult result = service.purgeBatch(
                Instant.now().minus(90, ChronoUnit.DAYS),
                Instant.now().minus(7, ChronoUnit.DAYS));

        assertThat(result).isEqualTo(new RetentionPurgeService.PurgeResult(3, 2, 2, 0));

        assertThat(countCategories(oldCategory)).isZero();
        assertThat(countProducts(oldProduct)).isZero();
        assertThat(countComments(oldProductLiveComment)).isZero();
        assertThat(countComments(oldProductOldComment)).isZero();

        assertThat(countCategories(liveCategory)).isOne();
        assertThat(countProducts(newProduct)).isOne();
        assertThat(countComments(newProductLiveComment)).isOne();
        assertThat(countComments(newProductOldComment)).isZero();

        assertThat(countProducts(liveProduct)).isOne();
        assertThat(countComments(liveComment)).isOne();

        assertThat(countCategories(expiredCategoryWithProduct)).isOne();
        assertThat(countProducts(retainedProduct)).isOne();

        assertThat(countCategories(expiredCategoryWithExpiredProduct)).isZero();
        assertThat(countProducts(expiredProductInExpiredCategory)).isZero();
    }

    @Test
    void should_purgeOnlyOldSentOutboxRows_when_batchRuns() {
        UUID purged = insertOutboxRow(OutboxState.SENT_DAYS_AGO, 8);
        UUID recent = insertOutboxRow(OutboxState.SENT_DAYS_AGO, 1);
        UUID unsent = insertOutboxRow(OutboxState.UNSENT, 8);
        UUID deadLettered = insertOutboxRow(OutboxState.DEAD_LETTERED, 8);

        RetentionPurgeService.PurgeResult result = service.purgeBatch(
                Instant.now().minus(90, ChronoUnit.DAYS),
                Instant.now().minus(7, ChronoUnit.DAYS));

        assertThat(result.outboxEvents()).isEqualTo(1);
        assertThat(countOutbox(purged)).isZero();
        assertThat(countOutbox(recent)).isOne();
        assertThat(countOutbox(unsent)).isOne();
        assertThat(countOutbox(deadLettered)).isOne();
    }

    @Test
    void should_notCreateSchedulerBean_when_purgeIsDisabledByDefault() {
        assertThat(applicationContext.getBeanNamesForType(RetentionPurgeScheduler.class)).isEmpty();
    }

    private enum OutboxState { SENT_DAYS_AGO, UNSENT, DEAD_LETTERED }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Renders a {@code deleted_at} expression: {@code 0} keeps the row live, a positive
     * number soft-deletes it that many days ago.
     */
    private static String deletedAt(int ageInDays) {
        return ageInDays <= 0 ? "null" : "now() - interval '" + ageInDays + " days'";
    }

    private UUID insertCategory(String name, int deletedAgeInDays) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into categories (id, name, deleted_at) values (?, ?, %s)"
                .formatted(deletedAt(deletedAgeInDays)), id, name);
        return id;
    }

    private UUID insertProduct(String name, UUID categoryId, int deletedAgeInDays) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into products (id, name, description, price, stock, category_id, deleted_at)
                values (?, ?, 'Seeded for the retention purge test', 1.00, true, ?, %s)
                """.formatted(deletedAt(deletedAgeInDays)), id, name, categoryId);
        return id;
    }

    private UUID insertComment(UUID productId, int deletedAgeInDays) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into comments (id, product_id, star, title, text, author, deleted_at)
                values (?, ?, 5, 'retention', 'Seeded comment body', 'retention-test', %s)
                """.formatted(deletedAt(deletedAgeInDays)), id, productId);
        return id;
    }

    private int countCategories(UUID id) {
        return jdbcTemplate.queryForObject("select count(*) from categories where id = ?", Integer.class, id);
    }

    private int countProducts(UUID id) {
        return jdbcTemplate.queryForObject("select count(*) from products where id = ?", Integer.class, id);
    }

    private int countComments(UUID id) {
        return jdbcTemplate.queryForObject("select count(*) from comments where id = ?", Integer.class, id);
    }

    /**
     * Seeds an outbox row. {@code ageInDays} moves {@code created_at}, {@code sent_at} and
     * {@code dead_lettered_at} together so the row's state and its age agree.
     */
    private UUID insertOutboxRow(OutboxState state, int ageInDays) {
        UUID id = UUID.randomUUID();
        String aggregateId = UUID.randomUUID().toString();
        String sentAt = state == OutboxState.SENT_DAYS_AGO
                ? "now() - interval '" + ageInDays + " days'" : "null";
        String deadLetteredAt = state == OutboxState.DEAD_LETTERED
                ? "now() - interval '" + ageInDays + " days'" : "null";
        jdbcTemplate.update("""
                insert into outbox_events (id, aggregate_type, aggregate_id, event_type, topic,
                    message_key, payload, headers, attempts, next_attempt_at, created_at,
                    sent_at, dead_lettered_at)
                values (?, 'Product', ?, 'created', 'catalog.product.created', ?,
                    '{}'::jsonb, '{}'::jsonb, 0, now() - interval '%d days',
                    now() - interval '%d days', %s, %s)
                """.formatted(ageInDays, ageInDays, sentAt, deadLetteredAt),
                id, aggregateId, aggregateId);
        return id;
    }

    private int countOutbox(UUID id) {
        return jdbcTemplate.queryForObject("select count(*) from outbox_events where id = ?",
                Integer.class, id);
    }
}
