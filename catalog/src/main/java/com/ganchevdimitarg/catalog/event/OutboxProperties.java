package com.ganchevdimitarg.catalog.event;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Tuning for the transactional-outbox relay and its retention purge.
 *
 * <p>{@code enabled} gates only the scheduled runs: {@link OutboxRelay#relayOnce()} always
 * delivers, so tests and operators can drive a run directly. {@code retention} sets how long
 * a delivered row is kept before the retention job deletes it.
 */
@ConfigurationProperties(prefix = "catalog.outbox")
public record OutboxProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("20") int batchSize,
        @DefaultValue("5s") Duration sendTimeout,
        @DefaultValue("7d") Duration retention) {
}
