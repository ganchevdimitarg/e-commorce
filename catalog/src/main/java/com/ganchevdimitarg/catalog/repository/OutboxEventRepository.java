package com.ganchevdimitarg.catalog.repository;

import com.ganchevdimitarg.catalog.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Claims a batch of due rows for this relay instance, oldest first, and sets the lease in
     * the same statement. The {@code not exists} clause admits at most one in-flight row per
     * aggregate, so a product's events reach Kafka in the order they were written; a
     * dead-lettered row is ignored because it will never be delivered. Expired leases are
     * claimable again, so a crashed relay loses no event.
     */
    @Query(value = """
            with due as (
                select e.id
                from outbox_events e
                where e.sent_at is null
                  and e.dead_lettered_at is null
                  and e.next_attempt_at <= now()
                  and (e.claimed_until is null or e.claimed_until <= now())
                  and not exists (
                      select 1 from outbox_events earlier
                      where earlier.aggregate_id = e.aggregate_id
                        and earlier.sent_at is null
                        and earlier.dead_lettered_at is null
                        and (earlier.created_at < e.created_at
                             or (earlier.created_at = e.created_at and earlier.id < e.id)))
                order by e.created_at, e.id
                limit :limit
                for update skip locked
            )
            update outbox_events o
            set claimed_until = :leaseUntil
            from due
            where o.id = due.id
            returning o.*
            """, nativeQuery = true)
    List<OutboxEvent> claimDueBatch(@Param("limit") int limit,
                                    @Param("leaseUntil") OffsetDateTime leaseUntil);

    long countBySentAtIsNullAndDeadLetteredAtIsNull();
}
