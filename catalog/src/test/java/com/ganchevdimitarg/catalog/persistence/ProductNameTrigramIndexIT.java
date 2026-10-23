package com.ganchevdimitarg.catalog.persistence;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the trigram index can serve the name-contains predicate. The {@code EXPLAIN}
 * mirrors the SQL that {@code ProductSpecifications.matching} plus Product's
 * {@code @SQLRestriction} emit — {@code lower(name) LIKE '%q%' ESCAPE '\' AND
 * deleted_at IS NULL} — with the pattern inlined.
 *
 * <p>It does not exercise Hibernate's bound-parameter statement. With a bind parameter
 * PostgreSQL may choose a generic plan, which cannot use a trigram index, so the runtime
 * plan for {@code GET /products?q=} is not asserted here; see {@code docs/decisions.md}
 * for the operational mitigation if measurements show search falling back to a scan.
 */
@Tag("integration")
class ProductNameTrigramIndexIT extends AbstractIntegrationTest {

    private static final int PRODUCTS = 2_000;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager transactionManager;

    private UUID categoryId;
    private String token;
    private String needle;

    @BeforeEach
    void seedProducts() {
        token = UUID.randomUUID().toString().substring(0, 8);
        // A second random token lands in a handful of rows only. Searching for the shared
        // token would match every seeded row, so the planner would prefer another index
        // over a trigram scan and the test would prove nothing.
        needle = UUID.randomUUID().toString().substring(0, 8);
        categoryId = UUID.randomUUID();
        jdbc.update("insert into categories (id, name, version) values (?, ?, 0)",
                categoryId, "trgm-cat-" + token);

        List<Object[]> rows = new ArrayList<>(PRODUCTS);
        for (int i = 0; i < PRODUCTS; i++) {
            String name = "trgm-" + token + "-" + i + (i % 500 == 0 ? "-" + needle : "");
            rows.add(new Object[]{UUID.randomUUID(), name, categoryId});
        }
        jdbc.batchUpdate("""
                insert into products (id, name, description, price, stock, category_id, version)
                values (?, ?, 'Seeded product for the trigram index test', 1, true, ?, 0)
                """, rows);
        // A bulk insert leaves every trigram in the GIN pending list, and a query has to
        // scan that list linearly; VACUUM flushes it and refreshes the planner statistics,
        // so the index is costed like an indexed table rather than a fresh one.
        jdbc.execute("VACUUM (ANALYZE) products");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from products where category_id = ?", categoryId);
        jdbc.update("delete from categories where id = ?", categoryId);
    }

    @Test
    void should_useTrigramIndex_when_nameContainsSearchPlans() {
        assertThat(jdbc.queryForObject("""
                select count(*) from pg_indexes
                where schemaname = current_schema() and indexname = 'idx_products_name_trgm'
                """, Integer.class))
                .as("V20 must create idx_products_name_trgm")
                .isEqualTo(1);

        // The pattern is inlined rather than bound: with a bind parameter PostgreSQL may
        // choose a generic plan, which cannot use a trigram index. The needle is generated
        // from a UUID ([0-9a-f-]), so inlining is safe.
        String sql = "EXPLAIN SELECT id FROM products "
                + "WHERE lower(name) LIKE '%" + needle + "%' ESCAPE '\\' "
                + "AND deleted_at IS NULL";

        List<String> plan = new TransactionTemplate(transactionManager).execute(status -> {
            jdbc.execute("SET LOCAL enable_seqscan = off");
            return jdbc.queryForList(sql, String.class);
        });

        assertThat(plan)
                .isNotNull()
                .isNotEmpty()
                .anySatisfy(line -> assertThat(line).contains("idx_products_name_trgm"));
        assertThat(plan).noneMatch(line -> line.contains("Seq Scan on products"));
    }
}
