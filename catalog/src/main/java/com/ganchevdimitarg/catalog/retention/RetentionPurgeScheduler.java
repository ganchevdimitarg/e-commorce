package com.ganchevdimitarg.catalog.retention;

import com.ganchevdimitarg.catalog.event.OutboxProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * Runs the retention purge on the configured cron, batching until the cap is reached or a
 * batch deletes nothing.
 *
 * <p>Two independent windows are applied: soft-deleted rows older than
 * {@code catalog.retention.retention}, and delivered outbox rows sent before
 * {@code catalog.outbox.retention}.
 *
 * <p>The bean exists only when {@code catalog.retention.purge-enabled=true}, so the default
 * configuration schedules no purge at all. Each iteration calls the proxied
 * {@link RetentionPurgeService}, so every batch commits in its own transaction; a failing
 * batch is logged and ends the run without propagating to the scheduler thread, and the
 * next scheduled run retries.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "catalog.retention", name = "purge-enabled", havingValue = "true")
public class RetentionPurgeScheduler {

    private final RetentionPurgeService service;
    private final RetentionProperties properties;
    private final OutboxProperties outboxProperties;
    private final Clock clock;

    @Scheduled(cron = "${catalog.retention.cron:0 0 3 * * *}")
    public void purge() {
        Instant cutoff = clock.instant().minus(properties.retention());
        Instant outboxCutoff = clock.instant().minus(outboxProperties.retention());
        try {
            for (int batch = 0; batch < properties.maxBatchesPerRun(); batch++) {
                if (service.purgeBatch(cutoff, outboxCutoff).isEmpty()) {
                    return;
                }
            }
            log.warn("Retention purge reached the {} batch cap; more expired rows may remain",
                    properties.maxBatchesPerRun());
        } catch (RuntimeException e) {
            log.error("Retention purge run failed at cutoff {} (outbox {})", cutoff, outboxCutoff, e);
        }
    }
}
