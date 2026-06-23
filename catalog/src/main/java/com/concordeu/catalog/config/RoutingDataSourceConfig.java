package com.concordeu.catalog.config;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

@Slf4j
@Configuration
@Profile("!test")
public class RoutingDataSourceConfig {

    private static final long HEALTH_PROBE_TTL_MS = 2_000;

    @Bean
    @FlywayDataSource
    public DataSource writerDataSource(
            @Value("${catalog.datasource.writer.url}") String url,
            @Value("${POSTGRES_USER}") String user,
            @Value("${POSTGRES_PASSWORD}") String password) {
        return hikari(url, user, password, "catalog-writer", 10);
    }

    @Bean
    public DataSource readerDataSource(
            @Value("${catalog.datasource.reader.url}") String url,
            @Value("${POSTGRES_USER}") String user,
            @Value("${POSTGRES_PASSWORD}") String password) {
        HikariDataSource ds = hikari(url, user, password, "catalog-reader", 20);
        ds.setConnectionTimeout(2_000);
        ds.setValidationTimeout(1_000);
        return ds;
    }

    @Bean
    @Primary
    public DataSource dataSource(DataSource writerDataSource, DataSource readerDataSource) {
        BooleanSupplier replicaHealthy = cachedHealthProbe(readerDataSource);
        DataSourceRouter router = new DataSourceRouter(replicaHealthy);
        router.setTargetDataSources(Map.of(
                DataSourceRouter.Route.WRITER, writerDataSource,
                DataSourceRouter.Route.READER, readerDataSource));
        router.setDefaultTargetDataSource(writerDataSource);
        router.afterPropertiesSet();
        return new LazyConnectionDataSourceProxy(router);
    }

    private static HikariDataSource hikari(String url, String user, String password,
                                           String poolName, int maxPoolSize) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(user);
        ds.setPassword(password);
        ds.setPoolName(poolName);
        ds.setMaximumPoolSize(maxPoolSize);
        return ds;
    }

    /**
     * Wraps the raw health probe in a TTL cache so the replica is probed at most
     * once every {@link #HEALTH_PROBE_TTL_MS} milliseconds. Between probes the
     * last known result is returned, avoiding per-transaction connection churn.
     */
    private static BooleanSupplier cachedHealthProbe(DataSource readerDataSource) {
        ReentrantLock probeLock = new ReentrantLock();
        long[] lastCheckTime = {0};
        boolean[] lastResult = {true};

        return () -> {
            long now = System.currentTimeMillis();
            if (now - lastCheckTime[0] >= HEALTH_PROBE_TTL_MS) {
                if (probeLock.tryLock()) {
                    try {
                        if (now - lastCheckTime[0] >= HEALTH_PROBE_TTL_MS) {
                            lastResult[0] = isHealthy(readerDataSource);
                            lastCheckTime[0] = now;
                        }
                    } finally {
                        probeLock.unlock();
                    }
                }
            }
            return lastResult[0];
        };
    }

    private static boolean isHealthy(DataSource readerDataSource) {
        try (var c = readerDataSource.getConnection()) {
            return c.isValid(1);
        } catch (Exception e) {
            log.warn("Replica health probe failed: {}", e.getMessage(), e);
            return false;
        }
    }
}
