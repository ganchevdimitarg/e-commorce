package com.ganchevdimitarg.catalog.config;

import com.ganchevdimitarg.catalog.health.ReplicaHealthProbe;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
}
