package com.ganchevdimitarg.catalog.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class DataSourceRouterTest {

    @AfterEach
    void clear() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void should_routeToReader_when_currentTransactionReadOnly() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        DataSourceRouter router = new DataSourceRouter(true); // replica healthy
        assertThat(router.resolveRoute()).isEqualTo(DataSourceRouter.Route.READER);
    }

    @Test
    void should_routeToWriter_when_transactionNotReadOnly() {
        DataSourceRouter router = new DataSourceRouter(true);
        assertThat(router.resolveRoute()).isEqualTo(DataSourceRouter.Route.WRITER);
    }

    @Test
    void should_fallbackToWriter_when_replicaUnhealthy() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        DataSourceRouter router = new DataSourceRouter(false); // replica unhealthy
        assertThat(router.resolveRoute()).isEqualTo(DataSourceRouter.Route.WRITER);
    }
}
