package com.concordeu.catalog.config;

import com.concordeu.catalog.AbstractIntegrationTest;
import com.concordeu.catalog.domain.Category;
import com.concordeu.catalog.repository.CategoryRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 7 routing safety net.
 *
 * <p>The {@link RoutingDataSourceConfig} read/write split is inactive under the
 * {@code test} profile ({@code @Profile("!test")}), so a live two-container
 * replication test is deferred to a manual release check. The routing-key logic
 * itself is unit-tested by {@code DataSourceRouterTest}.
 *
 * <p>What this IT proves on the single Testcontainers Postgres node is the
 * safety property that matters in production: a write attempted inside a
 * {@code @Transactional(readOnly = true)} boundary must <em>fail fast</em>.
 * Spring marks the JDBC connection read-only for a read-only transaction;
 * PostgreSQL then rejects any write with SQLSTATE 25006
 * ("cannot execute &lt;stmt&gt; in a read-only transaction"). This guarantees a
 * routing bug that lands a write on the reader connection cannot silently
 * succeed — it errors loudly instead. Removing {@code setReadOnly(true)} below
 * would let the insert commit and turn the fail-fast assertion RED.
 */
@Tag("integration")
class RoutingIntegrationIT extends AbstractIntegrationTest {

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    CategoryRepository categoryRepository;

    @Test
    void should_failFast_when_writeAttemptedInReadOnlyTransaction() {
        TransactionTemplate readOnlyTx = new TransactionTemplate(transactionManager);
        readOnlyTx.setReadOnly(true);

        // PostgreSQL rejects the flush with SQLSTATE 25006; the message
        // "cannot execute INSERT in a read-only transaction" is the load-bearing
        // proof that the connection was put in read-only mode by the tx boundary.
        assertThatThrownBy(() -> readOnlyTx.executeWithoutResult(status -> {
            Category category = new Category();
            category.setName("ro-guard");
            categoryRepository.saveAndFlush(category);
        }))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("cannot execute INSERT in a read-only transaction");

        assertThat(categoryRepository.findByName("ro-guard")).isEmpty();
    }

    @Test
    void should_allowReads_when_insideReadOnlyTransaction() {
        TransactionTemplate readOnlyTx = new TransactionTemplate(transactionManager);
        readOnlyTx.setReadOnly(true);

        long count = readOnlyTx.execute(status -> categoryRepository.count());

        assertThat(count).isGreaterThanOrEqualTo(0);
    }
}
