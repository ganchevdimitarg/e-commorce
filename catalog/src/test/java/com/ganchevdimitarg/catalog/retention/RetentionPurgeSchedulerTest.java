package com.ganchevdimitarg.catalog.retention;

import com.ganchevdimitarg.catalog.event.OutboxProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the run loop: the 90-day cutoff, the batch cap, the stop-on-empty rule and
 * exception isolation, so one failing batch cannot kill the scheduler thread.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class RetentionPurgeSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");
    private static final Instant CUTOFF = Instant.parse("2026-07-23T00:00:00Z");
    private static final Instant OUTBOX_CUTOFF = Instant.parse("2026-10-14T00:00:00Z");

    @Mock
    RetentionPurgeService service;

    private RetentionPurgeScheduler newScheduler(int maxBatchesPerRun) {
        return new RetentionPurgeScheduler(service,
                new RetentionProperties(true, Duration.ofDays(90), 500, maxBatchesPerRun, "0 0 3 * * *"),
                new OutboxProperties(true, 20, Duration.ofSeconds(5),
                        Duration.ofMinutes(1), Duration.ofDays(7)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void should_subtractRetentionWindow_when_computingCutoff() {
        when(service.purgeBatch(CUTOFF, OUTBOX_CUTOFF))
                .thenReturn(new RetentionPurgeService.PurgeResult(0, 0, 0, 0));

        newScheduler(100).purge();

        verify(service).purgeBatch(CUTOFF, OUTBOX_CUTOFF);
    }

    @Test
    void should_subtractOutboxRetentionWindow_when_computingOutboxCutoff() {
        Instant tenDayOutboxCutoff = Instant.parse("2026-10-11T00:00:00Z");
        when(service.purgeBatch(CUTOFF, tenDayOutboxCutoff))
                .thenReturn(new RetentionPurgeService.PurgeResult(0, 0, 0, 0));

        new RetentionPurgeScheduler(service,
                new RetentionProperties(true, Duration.ofDays(90), 500, 100, "0 0 3 * * *"),
                new OutboxProperties(true, 20, Duration.ofSeconds(5),
                        Duration.ofMinutes(1), Duration.ofDays(10)),
                Clock.fixed(NOW, ZoneOffset.UTC)).purge();

        verify(service).purgeBatch(CUTOFF, tenDayOutboxCutoff);
    }

    @Test
    void should_continuePurge_when_batchDeletesOnlyOutboxRows() {
        when(service.purgeBatch(CUTOFF, OUTBOX_CUTOFF)).thenReturn(
                new RetentionPurgeService.PurgeResult(0, 0, 0, 5),
                new RetentionPurgeService.PurgeResult(0, 0, 0, 0));

        newScheduler(100).purge();

        verify(service, times(2)).purgeBatch(CUTOFF, OUTBOX_CUTOFF);
    }

    @Test
    void should_stopBeforeCap_when_batchDeletesNothing() {
        when(service.purgeBatch(CUTOFF, OUTBOX_CUTOFF)).thenReturn(
                new RetentionPurgeService.PurgeResult(2, 1, 0, 0),
                new RetentionPurgeService.PurgeResult(0, 0, 0, 0));

        newScheduler(100).purge();

        verify(service, times(2)).purgeBatch(CUTOFF, OUTBOX_CUTOFF);
    }

    @Test
    void should_stopAtCap_when_batchesKeepDeleting() {
        when(service.purgeBatch(CUTOFF, OUTBOX_CUTOFF))
                .thenReturn(new RetentionPurgeService.PurgeResult(1, 1, 1, 0));

        newScheduler(3).purge();

        verify(service, times(3)).purgeBatch(CUTOFF, OUTBOX_CUTOFF);
    }

    @Test
    void should_swallowException_when_batchFails() {
        when(service.purgeBatch(any(), any())).thenThrow(new IllegalStateException("database down"));

        assertThatCode(() -> newScheduler(100).purge()).doesNotThrowAnyException();

        verify(service, times(1)).purgeBatch(any(), any());
    }
}
