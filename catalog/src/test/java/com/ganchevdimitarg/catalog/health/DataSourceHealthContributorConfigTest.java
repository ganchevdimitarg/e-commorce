package com.ganchevdimitarg.catalog.health;

import com.ganchevdimitarg.catalog.config.RoutingDataSourceConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.jdbc.autoconfigure.health.DataSourceHealthContributorAutoConfiguration;
import org.springframework.boot.jdbc.health.DataSourceHealthIndicator;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Regression test for the readiness {@code db} contributor.
 *
 * <p>Boot's {@link DataSourceHealthContributorAutoConfiguration} resolves every
 * autowire-candidate {@link DataSource} and builds a composite when there is more than
 * one, so with the writer, the reader and the routing proxy all exposed, an unreachable
 * replica would drive readiness {@code DOWN}. Catalog must register a writer-only
 * {@code dbHealthContributor} instead.
 */
@Tag("unit")
class DataSourceHealthContributorConfigTest {

    @Test
    void should_reportDbUp_when_replicaIsUnreachable() throws Exception {
        DataSource writer = mock(DataSource.class);
        DataSource reader = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        when(writer.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
        when(connection.isValid(0)).thenReturn(true);
        when(reader.getConnection()).thenThrow(new SQLException("Connection refused"));
        // Drop the stubbing invocation so the final assertion covers production calls only.
        clearInvocations(reader);

        MockedDataSourcesConfig.writer = writer;
        MockedDataSourcesConfig.reader = reader;

        new ApplicationContextRunner()
                .withPropertyValues(
                        "catalog.datasource.writer.url=jdbc:postgresql://writer:5432/catalog",
                        "catalog.datasource.reader.url=jdbc:postgresql://reader:5432/catalog",
                        "POSTGRES_USER=catalog",
                        "POSTGRES_PASSWORD=secret")
                .withConfiguration(AutoConfigurations.of(
                        DataSourceHealthContributorAutoConfiguration.class))
                .withUserConfiguration(MockedDataSourcesConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    HealthContributor contributor =
                            context.getBean("dbHealthContributor", HealthContributor.class);

                    assertThat(contributor)
                            .as("db must check the writer only, not a composite over "
                                    + "writer, reader and routing proxy")
                            .isInstanceOf(DataSourceHealthIndicator.class);
                    assertThat(((HealthIndicator) contributor).health().getStatus())
                            .isEqualTo(Status.UP);
                    verifyNoInteractions(reader);
                });
    }

    /**
     * Reuses the production wiring while substituting the writer and reader beans with
     * mocks. A plain {@code withBean} registration cannot be used because the
     * configuration class would re-register the same bean names and win.
     */
    @Configuration(proxyBeanMethods = false)
    static class MockedDataSourcesConfig extends RoutingDataSourceConfig {

        static DataSource writer;
        static DataSource reader;

        @Override
        @Bean
        public DataSource writerDataSource(
                @Value("${catalog.datasource.writer.url}") String url,
                @Value("${POSTGRES_USER}") String user,
                @Value("${POSTGRES_PASSWORD}") String password) {
            return writer;
        }

        @Override
        @Bean
        public DataSource readerDataSource(
                @Value("${catalog.datasource.reader.url}") String url,
                @Value("${POSTGRES_USER}") String user,
                @Value("${POSTGRES_PASSWORD}") String password) {
            return reader;
        }
    }
}
