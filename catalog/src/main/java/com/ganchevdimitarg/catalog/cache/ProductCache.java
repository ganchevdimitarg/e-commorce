package com.ganchevdimitarg.catalog.cache;

import com.ganchevdimitarg.catalog.dto.product.ProductResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Maintains the {@code product} cache after the surrounding transaction commits.
 *
 * <p>Writing to the cache inside a transactional method can expose rolled-back state and
 * race a concurrent read, so puts and evictions are deferred until after commit. The
 * after-commit put is what makes a just-written product readable from the cache instead
 * of a lagging replica.
 */
@Component
@RequiredArgsConstructor
public class ProductCache {

    private static final String CACHE = "product";

    private final CacheManager cacheManager;

    public void put(UUID productId, ProductResponseDto product) {
        afterCommit(() -> {
            Cache cache = cacheManager.getCache(CACHE);
            if (cache != null) {
                cache.put(productId, product);
            }
        });
    }

    public void evict(UUID productId) {
        afterCommit(() -> {
            Cache cache = cacheManager.getCache(CACHE);
            if (cache != null) {
                cache.evict(productId);
            }
        });
    }

    public void evictAll() {
        afterCommit(() -> {
            Cache cache = cacheManager.getCache(CACHE);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
