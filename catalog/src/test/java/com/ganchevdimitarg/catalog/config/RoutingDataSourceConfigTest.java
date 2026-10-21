package com.ganchevdimitarg.catalog.config;

import com.ganchevdimitarg.catalog.health.ReplicaHealthProbe;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class RoutingDataSourceConfigTest {

    @Test
    void should_returnLazyProxy_when_dataSourceBeanCreated() {
        DataSource writer = mock(DataSource.class);
        DataSource reader = mock(DataSource.class);
        ReplicaHealthProbe probe = new ReplicaHealthProbe(reader);

        RoutingDataSourceConfig config = new RoutingDataSourceConfig();
        DataSource result = config.dataSource(writer, reader, probe);

        assertThat(result).isInstanceOf(LazyConnectionDataSourceProxy.class);
    }

    @Test
    void should_buildProbeFromReader_when_replicaHealthProbeBeanCreated() throws Exception {
        DataSource reader = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(reader.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(true);

        RoutingDataSourceConfig config = new RoutingDataSourceConfig();
        ReplicaHealthProbe probe = config.replicaHealthProbe(reader);

        assertThat(probe).isInstanceOf(ReplicaHealthProbe.class);
        assertThat(probe.getAsBoolean()).isTrue();
        verify(reader).getConnection();
    }
}
