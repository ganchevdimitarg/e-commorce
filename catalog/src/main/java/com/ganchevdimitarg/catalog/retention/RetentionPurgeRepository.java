package com.ganchevdimitarg.catalog.retention;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Hard-deletes expired rows with native SQL: soft-deleted business rows and delivered
 * outbox rows.
 *
 * <p>JPA cannot do this: every entity carries {@code @SQLRestriction("deleted_at IS NULL")},
 * so JPQL and derived deletes never see the expired rows. Deletes run in foreign-key order —
 * comments first, then products, then categories that no longer hold any product row — and
 * each selection is bounded by the caller's batch size, while a selected product's comments
 * are all deleted with it.
 */
@Repository
@RequiredArgsConstructor
public class RetentionPurgeRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Deletes a batch of independently soft-deleted comments that expired before the cutoff.
     */
    public int deleteExpiredComments(Instant cutoff, int limit) {
        return jdbcTemplate.update("""
                delete from comments where id in (
                    select id from comments
                    where deleted_at is not null and deleted_at < ?
                    order by deleted_at limit ?)
                """, timestamp(cutoff), limit);
    }

    /**
     * Finds the ids of products soft-deleted before the cutoff, oldest first. The service
     * deletes their comments and the products themselves in the same logical batch.
     */
    public List<UUID> findExpiredProductIds(Instant cutoff, int limit) {
        return jdbcTemplate.queryForList("""
                select id from products
                where deleted_at is not null and deleted_at < ?
                order by deleted_at limit ?
                """, UUID.class, timestamp(cutoff), limit);
    }

    /**
     * Deletes every comment of the given products, live or soft-deleted, so the product
     * rows can go without violating the foreign key.
     */
    public int deleteCommentsForProducts(List<UUID> productIds) {
        return jdbcTemplate.update("delete from comments where product_id = any (?)",
                statement -> statement.setArray(1, uuidArray(statement, productIds)));
    }

    /**
     * Deletes the given expired products.
     */
    public int deleteProducts(List<UUID> productIds) {
        return jdbcTemplate.update("delete from products where id = any (?)",
                statement -> statement.setArray(1, uuidArray(statement, productIds)));
    }

    /**
     * Deletes a batch of expired categories that no product row still references, including
     * soft-deleted products.
     */
    public int deleteEmptyExpiredCategories(Instant cutoff, int limit) {
        return jdbcTemplate.update("""
                delete from categories c
                where c.id in (
                    select c2.id from categories c2
                    where c2.deleted_at is not null and c2.deleted_at < ?
                      and not exists (select 1 from products p where p.category_id = c2.id)
                    order by c2.deleted_at limit ?)
                """, timestamp(cutoff), limit);
    }

    /**
     * Deletes a batch of delivered outbox rows that were sent before the cutoff.
     * Dead-lettered rows keep {@code sent_at} NULL and are therefore never selected.
     */
    public int deleteSentOutboxEvents(Instant cutoff, int limit) {
        return jdbcTemplate.update("""
                delete from outbox_events where id in (
                    select id from outbox_events
                    where sent_at is not null and sent_at < ?
                    order by sent_at limit ?)
                """, timestamp(cutoff), limit);
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Array uuidArray(PreparedStatement statement, List<UUID> ids) throws SQLException {
        return statement.getConnection().createArrayOf("uuid", ids.toArray());
    }
}
