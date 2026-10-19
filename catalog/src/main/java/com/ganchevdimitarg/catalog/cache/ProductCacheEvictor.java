package com.ganchevdimitarg.catalog.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Evicts the {@code product} cache after the surrounding transaction commits.
 *
 * <p>Evicting inside a transactional method can race a concurrent read that repopulates
 * the cache with pre-commit data, so the eviction is deferred until after commit.
 */
@Component
@RequiredArgsConstructor
public class ProductCacheEvictor {

    private static final String CACHE = "product";

    private final CacheManager cacheManager;

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
