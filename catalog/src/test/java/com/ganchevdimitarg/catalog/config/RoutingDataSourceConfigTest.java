package com.ganchevdimitarg.catalog.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class RoutingDataSourceConfigTest {

    @Test
    void should_returnLazyProxy_when_dataSourceBeanCreated() throws Exception {
        DataSource writer = mock(DataSource.class);
        DataSource reader = mock(DataSource.class);

        // Mock connection for reader health probe
        Connection conn = mock(Connection.class);
        when(reader.getConnection()).thenReturn(conn);
        when(conn.isValid(1)).thenReturn(true);

        RoutingDataSourceConfig config = new RoutingDataSourceConfig();
        DataSource result = config.dataSource(writer, reader);

        assertThat(result).isInstanceOf(LazyConnectionDataSourceProxy.class);
    }

    @Test
    void should_returnTrue_when_replicaIsHealthy() throws Exception {
        DataSource reader = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(reader.getConnection()).thenReturn(conn);
        when(conn.isValid(1)).thenReturn(true);

        boolean healthy = invokeIsHealthy(reader);

        assertThat(healthy).isTrue();
        verify(conn).close();
    }

    @Test
    void should_returnFalse_when_replicaConnectionFails() throws Exception {
        DataSource reader = mock(DataSource.class);
        when(reader.getConnection()).thenThrow(new SQLException("Connection refused"));

        boolean healthy = invokeIsHealthy(reader);

        assertThat(healthy).isFalse();
    }

    @Test
    void should_returnFalse_when_connectionIsNotValid() throws Exception {
        DataSource reader = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(reader.getConnection()).thenReturn(conn);
        when(conn.isValid(1)).thenReturn(false);

        boolean healthy = invokeIsHealthy(reader);

        assertThat(healthy).isFalse();
        verify(conn).close();
    }

    @Test
    void should_cacheProbeResult_when_withinTtl() throws Exception {
        DataSource reader = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(reader.getConnection()).thenReturn(conn);
        when(conn.isValid(1)).thenReturn(true);

        BooleanSupplier probe = invokeCachedHealthProbe(reader);

        // First call probes the datasource
        assertThat(probe.getAsBoolean()).isTrue();
        // Second call within TTL should use cached result
        assertThat(probe.getAsBoolean()).isTrue();

        // Connection should only have been obtained once (cached)
        verify(reader, times(1)).getConnection();
    }

    @Test
    void should_returnCachedFalse_when_replicaWasUnhealthy() throws Exception {
        DataSource reader = mock(DataSource.class);
        when(reader.getConnection()).thenThrow(new SQLException("down"));

        BooleanSupplier probe = invokeCachedHealthProbe(reader);

        assertThat(probe.getAsBoolean()).isFalse();
        // Second call within TTL returns cached false
        assertThat(probe.getAsBoolean()).isFalse();

        // Only probed once
        verify(reader, times(1)).getConnection();
    }

    private static boolean invokeIsHealthy(DataSource ds) throws Exception {
        Method method = RoutingDataSourceConfig.class.getDeclaredMethod("isHealthy", DataSource.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, ds);
    }

    @SuppressWarnings("unchecked")
    private static BooleanSupplier invokeCachedHealthProbe(DataSource ds) throws Exception {
        Method method = RoutingDataSourceConfig.class.getDeclaredMethod("cachedHealthProbe", DataSource.class);
        method.setAccessible(true);
        return (BooleanSupplier) method.invoke(null, ds);
    }
}
