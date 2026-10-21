package com.ganchevdimitarg.catalog.cache;

import com.ganchevdimitarg.catalog.dto.product.ProductResponseDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ProductCacheTest {

    private static final String CACHE = "product";
    private static final UUID PRODUCT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000d00d");

    @Mock
    CacheManager cacheManager;

    @Mock
    Cache cache;

    private ProductCache productCache;

    @BeforeEach
    void setUp() {
        productCache = new ProductCache(cacheManager);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void should_putAfterCommit_when_transactionActive() {
        when(cacheManager.getCache(CACHE)).thenReturn(cache);
        ProductResponseDto dto = productDto();

        productCache.put(PRODUCT_ID, dto);

        verify(cache, never()).put(any(), any());

        runAfterCommit();

        verify(cache).put(PRODUCT_ID, dto);
    }

    @Test
    void should_notPut_when_transactionRollsBack() {
        productCache.put(PRODUCT_ID, productDto());

        runAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verifyNoInteractions(cache);
        verifyNoInteractions(cacheManager);
    }

    @Test
    void should_evictAfterCommit_when_transactionActive() {
        when(cacheManager.getCache(CACHE)).thenReturn(cache);

        productCache.evict(PRODUCT_ID);

        verify(cache, never()).evict(any());

        runAfterCommit();

        verify(cache).evict(PRODUCT_ID);
    }

    @Test
    void should_runImmediately_when_noTransactionActive() {
        TransactionSynchronizationManager.clearSynchronization();
        when(cacheManager.getCache(CACHE)).thenReturn(cache);
        ProductResponseDto dto = productDto();

        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();

        productCache.put(PRODUCT_ID, dto);

        verify(cache).put(PRODUCT_ID, dto);
    }

    @Test
    void should_notThrow_when_backingCacheThrows() {
        TransactionSynchronizationManager.clearSynchronization();
        when(cacheManager.getCache(CACHE)).thenReturn(cache);

        RedisConnectionFailureException failure = new RedisConnectionFailureException("down");
        doThrow(failure).when(cache).put(any(), any());
        doThrow(failure).when(cache).evict(any());
        doThrow(failure).when(cache).clear();

        assertThatCode(() -> {
            productCache.put(PRODUCT_ID, productDto());
            productCache.evict(PRODUCT_ID);
            productCache.evictAll();
        }).doesNotThrowAnyException();

        verify(cache).put(any(), any());
        verify(cache).evict(any());
        verify(cache).clear();
    }

    private static void runAfterCommit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }

    private static void runAfterCompletion(int status) {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(status));
    }

    private static ProductResponseDto productDto() {
        return new ProductResponseDto(PRODUCT_ID, "mouse", "Wireless mouse", BigDecimal.TEN,
                true, "test-chars", null, List.of(), 7L, 0.0, 0);
    }
}
