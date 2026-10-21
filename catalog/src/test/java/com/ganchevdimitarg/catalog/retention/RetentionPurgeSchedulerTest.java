package com.ganchevdimitarg.catalog.retention;

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

    @Mock
    RetentionPurgeService service;

    private RetentionPurgeScheduler newScheduler(int maxBatchesPerRun) {
        return new RetentionPurgeScheduler(service,
                new RetentionProperties(true, Duration.ofDays(90), 500, maxBatchesPerRun, "0 0 3 * * *"),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void should_subtractRetentionWindow_when_computingCutoff() {
        when(service.purgeBatch(CUTOFF)).thenReturn(new RetentionPurgeService.PurgeResult(0, 0, 0));

        newScheduler(100).purge();

        verify(service).purgeBatch(CUTOFF);
    }

    @Test
    void should_stopBeforeCap_when_batchDeletesNothing() {
        when(service.purgeBatch(CUTOFF)).thenReturn(
                new RetentionPurgeService.PurgeResult(2, 1, 0),
                new RetentionPurgeService.PurgeResult(0, 0, 0));

        newScheduler(100).purge();

        verify(service, times(2)).purgeBatch(CUTOFF);
    }

    @Test
    void should_stopAtCap_when_batchesKeepDeleting() {
        when(service.purgeBatch(CUTOFF)).thenReturn(new RetentionPurgeService.PurgeResult(1, 1, 1));

        newScheduler(3).purge();

        verify(service, times(3)).purgeBatch(CUTOFF);
    }

    @Test
    void should_swallowException_when_batchFails() {
        when(service.purgeBatch(any())).thenThrow(new IllegalStateException("database down"));

        assertThatCode(() -> newScheduler(100).purge()).doesNotThrowAnyException();

        verify(service, times(1)).purgeBatch(any());
    }
}
