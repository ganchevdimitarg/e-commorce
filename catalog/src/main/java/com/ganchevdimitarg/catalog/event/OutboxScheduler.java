package com.ganchevdimitarg.catalog.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the outbox relay on a fixed delay.
 *
 * <p>Periodic runs are gated by {@code catalog.outbox.enabled} so tests can disable them and
 * call {@link OutboxRelay#relayOnce()} directly; the relay itself ignores the flag.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxRelay relay;
    private final OutboxProperties properties;

    @Scheduled(fixedDelayString = "${catalog.outbox.relay-interval-ms:1000}")
    public void relay() {
        if (!properties.enabled()) {
            return;
        }
        try {
            relay.relayOnce();
        } catch (RuntimeException e) {
            log.error("Outbox relay run failed", e);
        }
    }
}
