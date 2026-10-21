package com.ganchevdimitarg.catalog.health;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class ReplicaHealthProbeTest {

    @Test
    void should_returnTrue_when_replicaIsHealthy() throws Exception {
        DataSource reader = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(reader.getConnection()).thenReturn(conn);
        when(conn.isValid(1)).thenReturn(true);

        ReplicaHealthProbe probe = new ReplicaHealthProbe(reader);

        assertThat(probe.isHealthy()).isTrue();
        verify(conn).close();
    }

    @Test
    void should_returnFalse_when_replicaConnectionFails() throws Exception {
        DataSource reader = mock(DataSource.class);
        when(reader.getConnection()).thenThrow(new SQLException("Connection refused"));

        ReplicaHealthProbe probe = new ReplicaHealthProbe(reader);

        assertThat(probe.isHealthy()).isFalse();
    }

    @Test
    void should_returnFalse_when_connectionIsNotValid() throws Exception {
        DataSource reader = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(reader.getConnection()).thenReturn(conn);
        when(conn.isValid(1)).thenReturn(false);

        ReplicaHealthProbe probe = new ReplicaHealthProbe(reader);

        assertThat(probe.isHealthy()).isFalse();
        verify(conn).close();
    }

    @Test
    void should_cacheProbeResult_when_withinTtl() throws Exception {
        DataSource reader = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        when(reader.getConnection()).thenReturn(conn);
        when(conn.isValid(1)).thenReturn(true);

        ReplicaHealthProbe probe = new ReplicaHealthProbe(reader);

        // First call probes the datasource
        assertThat(probe.getAsBoolean()).isTrue();
        // Second call within TTL should use the cached result
        assertThat(probe.getAsBoolean()).isTrue();

        // Connection should only have been obtained once (cached)
        verify(reader, times(1)).getConnection();
    }

    @Test
    void should_returnCachedFalse_when_replicaWasUnhealthy() throws Exception {
        DataSource reader = mock(DataSource.class);
        when(reader.getConnection()).thenThrow(new SQLException("down"));

        ReplicaHealthProbe probe = new ReplicaHealthProbe(reader);

        assertThat(probe.getAsBoolean()).isFalse();
        // Second call within TTL returns cached false
        assertThat(probe.getAsBoolean()).isFalse();

        // Only probed once
        verify(reader, times(1)).getConnection();
    }
}
