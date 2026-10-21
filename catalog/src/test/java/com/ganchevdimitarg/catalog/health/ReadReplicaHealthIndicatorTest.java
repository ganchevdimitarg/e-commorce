package com.ganchevdimitarg.catalog.health;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("unit")
class ReadReplicaHealthIndicatorTest {

    private final ReplicaHealthProbe probe = mock(ReplicaHealthProbe.class);
    private final ReadReplicaHealthIndicator indicator = new ReadReplicaHealthIndicator(probe);

    @Test
    void should_reportReaderRoute_when_replicaIsReachable() {
        when(probe.getAsBoolean()).thenReturn(true);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("reader", "reachable")
                .containsEntry("route", "reader");
        verify(probe).getAsBoolean();
    }

    @Test
    void should_reportWriterFallback_when_replicaIsUnreachable() {
        when(probe.getAsBoolean()).thenReturn(false);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("reader", "unreachable")
                .containsEntry("route", "writer");
        verify(probe).getAsBoolean();
    }
}
