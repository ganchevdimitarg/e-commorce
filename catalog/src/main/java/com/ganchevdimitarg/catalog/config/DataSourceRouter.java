package com.ganchevdimitarg.catalog.config;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.function.BooleanSupplier;

public class DataSourceRouter extends AbstractRoutingDataSource {

    public enum Route { WRITER, READER }

    private final BooleanSupplier replicaHealthy;

    public DataSourceRouter(boolean replicaHealthy) {
        this.replicaHealthy = () -> replicaHealthy;
    }

    public DataSourceRouter(BooleanSupplier replicaHealthy) {
        this.replicaHealthy = replicaHealthy;
    }

    Route resolveRoute() {
        boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        if (readOnly && replicaHealthy.getAsBoolean()) {
            return Route.READER;
        }
        return Route.WRITER;   // writes, and the graceful-degradation fallback
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return resolveRoute();
    }
}
