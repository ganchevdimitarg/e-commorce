package com.ganchevdimitarg.catalog.config;

import com.ganchevdimitarg.catalog.health.ReplicaHealthProbe;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.flyway.autoconfigure.FlywayDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.Map;

@Configuration
@Profile("!test")
public class RoutingDataSourceConfig {

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
    public ReplicaHealthProbe replicaHealthProbe(
            @Qualifier("readerDataSource") DataSource readerDataSource) {
        return new ReplicaHealthProbe(readerDataSource);
    }

    @Bean
    @Primary
    public DataSource dataSource(DataSource writerDataSource, DataSource readerDataSource,
                                 ReplicaHealthProbe replicaHealthProbe) {
        DataSourceRouter router = new DataSourceRouter(replicaHealthProbe);
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
}
