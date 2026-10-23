package com.ganchevdimitarg.catalog.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class AfterCommitMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final AfterCommitMetrics metrics = new AfterCommitMetrics(registry);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void should_incrementImmediately_when_noTransactionIsActive() {
        metrics.increment("catalog.test.created");

        assertThat(registry.get("catalog.test.created").counter().count()).isEqualTo(1.0);
    }

    @Test
    void should_deferIncrement_when_transactionIsActive() {
        TransactionSynchronizationManager.initSynchronization();

        metrics.increment("catalog.test.created");

        // Nothing is registered until the commit callback runs, so the meter does not exist yet.
        assertThat(registry.find("catalog.test.created").counter()).isNull();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        assertThat(registry.get("catalog.test.created").counter().count()).isEqualTo(1.0);
    }

    @Test
    void should_notIncrement_when_transactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();

        metrics.increment("catalog.test.created");
        TransactionSynchronizationManager.clearSynchronization();

        assertThat(registry.find("catalog.test.created").counter()).isNull();
    }

    @Test
    void should_incrementByAmount_when_transactionCommits() {
        TransactionSynchronizationManager.initSynchronization();

        metrics.incrementBy("catalog.test.moved", 5);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        assertThat(registry.get("catalog.test.moved").counter().count()).isEqualTo(5.0);
    }
}
