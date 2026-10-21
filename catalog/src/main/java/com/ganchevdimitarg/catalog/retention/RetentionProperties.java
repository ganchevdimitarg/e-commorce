package com.ganchevdimitarg.catalog.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Tuning for the soft-delete retention purge.
 *
 * <p>The job is opt-in: {@code purgeEnabled} defaults to {@code false}, so no purge runs and
 * no {@link RetentionPurgeScheduler} bean exists until an environment opts in.
 */
@ConfigurationProperties(prefix = "catalog.retention")
public record RetentionProperties(
        @DefaultValue("false") boolean purgeEnabled,
        @DefaultValue("90d") Duration retention,
        @DefaultValue("500") int batchSize,
        @DefaultValue("100") int maxBatchesPerRun,
        @DefaultValue("0 0 3 * * *") String cron) {
}
