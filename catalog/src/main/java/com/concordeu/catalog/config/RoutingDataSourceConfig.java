package com.concordeu.catalog.config;

import com.zaxxer.hikari.HikariDataSource;
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
        return hikari(url, user, password, "catalog-reader", 20);
    }

    @Bean
    @Primary
    public DataSource dataSource(DataSource writerDataSource, DataSource readerDataSource) {
        DataSourceRouter router = new DataSourceRouter(() -> isHealthy(readerDataSource));
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

    private static boolean isHealthy(DataSource readerDataSource) {
        try (var c = readerDataSource.getConnection()) {
            return c.isValid(1);
        } catch (Exception e) {
            return false;
        }
    }
}
