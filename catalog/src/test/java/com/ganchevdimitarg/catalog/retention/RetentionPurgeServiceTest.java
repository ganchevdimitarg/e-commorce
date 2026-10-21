package com.ganchevdimitarg.catalog.retention;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the order of the native deletes, the configured batch size and the per-entity
 * purge counters. The cutoff is computed by {@link RetentionPurgeScheduler}; the service
 * receives it and passes it through unchanged.
 */
@Tag("unit")
@ExtendWith(MockitoExtension.class)
class RetentionPurgeServiceTest {

    private static final Instant CUTOFF = Instant.parse("2026-07-23T00:00:00Z");
    private static final UUID FIRST_PRODUCT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID SECOND_PRODUCT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    RetentionPurgeRepository repository;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private RetentionPurgeService newService(int batchSize) {
        return new RetentionPurgeService(repository,
                new RetentionProperties(true, Duration.ofDays(90), batchSize, 100, "0 0 3 * * *"),
                meterRegistry);
    }

    @Test
    void should_purgeInForeignKeyOrder_when_batchRuns() {
        when(repository.deleteExpiredComments(CUTOFF, 500)).thenReturn(3);
        when(repository.findExpiredProductIds(CUTOFF, 500))
                .thenReturn(List.of(FIRST_PRODUCT, SECOND_PRODUCT));
        when(repository.deleteCommentsForProducts(List.of(FIRST_PRODUCT, SECOND_PRODUCT))).thenReturn(4);
        when(repository.deleteProducts(List.of(FIRST_PRODUCT, SECOND_PRODUCT))).thenReturn(2);
        when(repository.deleteEmptyExpiredCategories(CUTOFF, 500)).thenReturn(1);

        RetentionPurgeService.PurgeResult result = newService(500).purgeBatch(CUTOFF);

        assertThat(result).isEqualTo(new RetentionPurgeService.PurgeResult(7, 2, 1));
        InOrder order = inOrder(repository);
        order.verify(repository).deleteExpiredComments(CUTOFF, 500);
        order.verify(repository).findExpiredProductIds(CUTOFF, 500);
        order.verify(repository).deleteCommentsForProducts(List.of(FIRST_PRODUCT, SECOND_PRODUCT));
        order.verify(repository).deleteProducts(List.of(FIRST_PRODUCT, SECOND_PRODUCT));
        order.verify(repository).deleteEmptyExpiredCategories(CUTOFF, 500);
    }

    @Test
    void should_passConfiguredBatchSize_when_purging() {
        when(repository.deleteExpiredComments(CUTOFF, 25)).thenReturn(0);
        when(repository.findExpiredProductIds(CUTOFF, 25)).thenReturn(List.of());
        when(repository.deleteEmptyExpiredCategories(CUTOFF, 25)).thenReturn(0);

        RetentionPurgeService.PurgeResult result = newService(25).purgeBatch(CUTOFF);

        assertThat(result).isEqualTo(new RetentionPurgeService.PurgeResult(0, 0, 0));
        verify(repository).deleteExpiredComments(CUTOFF, 25);
        verify(repository).findExpiredProductIds(CUTOFF, 25);
        verify(repository).deleteEmptyExpiredCategories(CUTOFF, 25);
        verify(repository, never()).deleteCommentsForProducts(anyList());
        verify(repository, never()).deleteProducts(anyList());
    }

    @Test
    void should_incrementPurgedCountersPerEntity_when_rowsAreDeleted() {
        when(repository.deleteExpiredComments(CUTOFF, 500)).thenReturn(3);
        when(repository.findExpiredProductIds(CUTOFF, 500)).thenReturn(List.of(FIRST_PRODUCT));
        when(repository.deleteCommentsForProducts(List.of(FIRST_PRODUCT))).thenReturn(4);
        when(repository.deleteProducts(List.of(FIRST_PRODUCT))).thenReturn(1);
        when(repository.deleteEmptyExpiredCategories(CUTOFF, 500)).thenReturn(2);

        newService(500).purgeBatch(CUTOFF);

        assertThat(meterRegistry.counter("catalog.retention.purged", "entity", "comments").count())
                .isEqualTo(7.0);
        assertThat(meterRegistry.counter("catalog.retention.purged", "entity", "products").count())
                .isEqualTo(1.0);
        assertThat(meterRegistry.counter("catalog.retention.purged", "entity", "categories").count())
                .isEqualTo(2.0);
    }
}
