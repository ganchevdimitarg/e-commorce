package com.ganchevdimitarg.catalog.health;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

/**
 * TTL-cached health probe for the read replica.
 *
 * <p>Shared by {@code DataSourceRouter} (route selection) and
 * {@link ReadReplicaHealthIndicator} (actuator health) so both read the same
 * cached verdict instead of opening a connection per call. The replica is
 * probed at most once every {@link #TTL_MS} milliseconds; between probes the
 * last known result is returned, avoiding per-transaction connection churn.
 *
 * <p>The first call always probes, and the initial result is optimistic
 * ({@code true}) so traffic can be served by the replica until proven
 * otherwise. If a probe is already in flight, callers get the last known
 * result rather than blocking.
 */
@Slf4j
public final class ReplicaHealthProbe implements BooleanSupplier {

    static final long TTL_MS = 2_000;

    private final DataSource readerDataSource;
    private final ReentrantLock probeLock = new ReentrantLock();

    private volatile long lastCheckTime;
    private volatile boolean lastResult = true;

    public ReplicaHealthProbe(DataSource readerDataSource) {
        this.readerDataSource = readerDataSource;
    }

    @Override
    public boolean getAsBoolean() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime >= TTL_MS) {
            if (probeLock.tryLock()) {
                try {
                    // Double-check under the lock: another thread may have probed already.
                    if (now - lastCheckTime >= TTL_MS) {
                        lastResult = isHealthy();
                        lastCheckTime = now;
                    }
                } finally {
                    probeLock.unlock();
                }
            }
        }
        return lastResult;
    }

    /**
     * Probes the replica directly, bypassing the cache.
     *
     * @return {@code true} when a connection can be obtained and validated
     *         within one second; {@code false} on any exception
     */
    boolean isHealthy() {
        try (var connection = readerDataSource.getConnection()) {
            return connection.isValid(1);
        } catch (Exception e) {
            log.warn("Replica health probe failed: {}", e.getMessage(), e);
            return false;
        }
    }
}
