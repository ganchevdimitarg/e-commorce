package com.ganchevdimitarg.catalog.repository;

import com.ganchevdimitarg.catalog.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Claims a batch of due, unsent rows for the relay, oldest first.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} lets concurrent relay instances claim disjoint
     * batches instead of blocking on rows another instance already holds.
     */
    @Query(value = """
            select * from outbox_events
            where sent_at is null and next_attempt_at <= now()
            order by created_at
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockUnsentBatch(@Param("limit") int limit);

    long countBySentAtIsNull();
}
