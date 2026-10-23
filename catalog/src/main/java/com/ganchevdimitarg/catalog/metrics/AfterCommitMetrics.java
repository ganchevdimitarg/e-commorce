package com.ganchevdimitarg.catalog.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Records business counters after the surrounding transaction commits, so a rolled-back
 * write is never counted. Outside a transaction the increment runs immediately, which keeps
 * plain unit tests meaningful.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AfterCommitMetrics {

    private final MeterRegistry meterRegistry;

    public void increment(String name) {
        incrementBy(name, 1);
    }

    public void incrementBy(String name, long amount) {
        afterCommit(() -> meterRegistry.counter(name).increment(amount));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runQuietly(action);
                }
            });
        } else {
            runQuietly(action);
        }
    }

    /**
     * A metrics failure must not fail a request whose transaction has already committed.
     */
    private void runQuietly(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Metric update failed; continuing: {}", e.getMessage());
        }
    }
}
