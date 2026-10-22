package com.ganchevdimitarg.catalog.category;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import com.ganchevdimitarg.catalog.cache.ProductCache;
import com.ganchevdimitarg.catalog.dto.category.MoveProductCommand;
import com.ganchevdimitarg.catalog.event.ProductTopics;
import com.ganchevdimitarg.catalog.service.category.CategoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts that both category-move paths write one {@code category-changed} event per moved
 * product into the outbox, inside the caller's transaction.
 *
 * <p>Postgres only: the move itself never contacts Kafka, and {@link ProductCache} is mocked
 * because the after-commit eviction would otherwise need Redis.
 */
@Tag("integration")
class CategoryMoveEventIT extends AbstractIntegrationTest {

    private static final String SOURCE_CATEGORY = "move-it-source";
    private static final String TARGET_CATEGORY = "move-it-target";
    private static final String MOUSE = "move-it-mouse";
    private static final String KEYBOARD = "move-it-keyboard";

    @MockitoBean
    ProductCache productCache;

    @Autowired
    CategoryService categoryService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate txTemplate;

    private UUID sourceId;
    private UUID targetId;
    private UUID mouseId;
    private UUID keyboardId;

    @BeforeEach
    void setUp() {
        sourceId = UUID.randomUUID();
        targetId = UUID.randomUUID();
        mouseId = UUID.randomUUID();
        keyboardId = UUID.randomUUID();

        insertCategory(sourceId, SOURCE_CATEGORY);
        insertCategory(targetId, TARGET_CATEGORY);
        insertProduct(mouseId, MOUSE, sourceId, "19.99");
        insertProduct(keyboardId, KEYBOARD, sourceId, "49.50");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM outbox_events WHERE aggregate_id IN (?, ?)",
                mouseId.toString(), keyboardId.toString());
        jdbc.update("DELETE FROM products WHERE id IN (?, ?)", mouseId, keyboardId);
        jdbc.update("DELETE FROM categories WHERE id IN (?, ?)", sourceId, targetId);
    }

    @Test
    @WithMockUser(authorities = "SCOPE_catalog.manage")
    void should_writeOneCategoryChangedEvent_when_oneProductMoves() {
        txTemplate.executeWithoutResult(status -> categoryService.moveOneProduct(
                new MoveProductCommand(SOURCE_CATEGORY, TARGET_CATEGORY, MOUSE)));

        List<Map<String, Object>> rows = categoryChangedRows();

        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.getFirst();
        assertThat(row.get("product_id")).isEqualTo(mouseId.toString());
        assertThat(row.get("event_type")).isEqualTo("category-changed");
        assertThat(row.get("topic")).isEqualTo(ProductTopics.PRODUCT_CATEGORY_CHANGED);
        assertThat(row.get("previous_category_name")).isEqualTo(SOURCE_CATEGORY);
        assertThat(row.get("category_name")).isEqualTo(TARGET_CATEGORY);
        assertThat(row.get("schema_version")).isEqualTo("1");
        // The move's bulk update increments the row version by one (0 -> 1)
        assertThat(row.get("version")).isEqualTo("1");
        assertThat(row.get("price")).isEqualTo("19.99");
        assertThat(row.get("in_stock")).isEqualTo("true");
    }

    @Test
    @WithMockUser(authorities = "SCOPE_catalog.manage")
    void should_writeOneCategoryChangedEventPerProduct_when_allProductsMove() {
        txTemplate.executeWithoutResult(
                status -> categoryService.moveAllProducts(SOURCE_CATEGORY, TARGET_CATEGORY));

        List<Map<String, Object>> rows = categoryChangedRows();

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(row -> row.get("product_id"))
                .containsExactlyInAnyOrder(mouseId.toString(), keyboardId.toString());
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("previous_category_name")).isEqualTo(SOURCE_CATEGORY);
            assertThat(row.get("category_name")).isEqualTo(TARGET_CATEGORY);
            assertThat(row.get("schema_version")).isEqualTo("1");
            assertThat(row.get("version")).isEqualTo("1");
        });
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM products WHERE category_id = ?", Integer.class, targetId))
                .isEqualTo(2);
    }

    @Test
    @WithMockUser(authorities = "SCOPE_catalog.manage")
    void should_writeOneEventPerProduct_when_largeCategoryMoves() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        String sourceName = "move-large-source-" + run;
        String targetName = "move-large-target-" + run;
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        insertCategory(sourceId, sourceName);
        insertCategory(targetId, targetName);
        for (int i = 0; i < 30; i++) {
            insertProduct(UUID.randomUUID(), "move-large-" + run + "-" + i, sourceId, "10.00");
        }

        try {
            txTemplate.executeWithoutResult(
                    status -> categoryService.moveAllProducts(sourceName, targetName));

            Integer events = jdbc.queryForObject("""
                    SELECT count(*) FROM outbox_events
                    WHERE topic = ? AND payload ->> 'previousCategoryName' = ?
                    """, Integer.class, ProductTopics.PRODUCT_CATEGORY_CHANGED, sourceName);
            assertThat(events).isEqualTo(30);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM products WHERE category_id = ?", Integer.class, targetId))
                    .isEqualTo(30);
        } finally {
            jdbc.update("DELETE FROM outbox_events WHERE topic = ? AND payload ->> 'previousCategoryName' = ?",
                    ProductTopics.PRODUCT_CATEGORY_CHANGED, sourceName);
            jdbc.update("DELETE FROM products WHERE category_id = ?", targetId);
            jdbc.update("DELETE FROM categories WHERE id IN (?, ?)", sourceId, targetId);
        }
    }

    @Test
    @WithMockUser(authorities = "SCOPE_catalog.manage")
    void should_writeNoEvents_when_sourceCategoryIsEmpty() {
        jdbc.update("DELETE FROM products WHERE id IN (?, ?)", mouseId, keyboardId);

        txTemplate.executeWithoutResult(
                status -> categoryService.moveAllProducts(SOURCE_CATEGORY, TARGET_CATEGORY));

        assertThat(categoryChangedRows()).isEmpty();
    }

    /**
     * jsonb canonicalises key order and whitespace, so every field is read through the
     * {@code ->>} operator rather than compared as payload bytes.
     */
    private List<Map<String, Object>> categoryChangedRows() {
        return jdbc.queryForList("""
                SELECT payload ->> 'productId' AS product_id,
                       event_type,
                       topic,
                       payload ->> 'previousCategoryName' AS previous_category_name,
                       payload ->> 'categoryName' AS category_name,
                       payload ->> 'version' AS version,
                       payload ->> 'schemaVersion' AS schema_version,
                       payload ->> 'price' AS price,
                       payload ->> 'inStock' AS in_stock
                FROM outbox_events
                WHERE topic = ?
                  AND payload ->> 'productId' IN (?, ?)
                ORDER BY payload ->> 'productId'
                """, ProductTopics.PRODUCT_CATEGORY_CHANGED, mouseId.toString(), keyboardId.toString());
    }

    private void insertCategory(UUID id, String name) {
        jdbc.update("""
                INSERT INTO categories (id, name, created_at, updated_at, version)
                VALUES (?, ?, now(), now(), 0)
                """, id, name);
    }

    private void insertProduct(UUID id, String name, UUID categoryId, String price) {
        jdbc.update("""
                INSERT INTO products
                    (id, name, description, price, stock, characteristics, category_id, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, true, ?, ?, now(), now(), 0)
                """, id, name, "Category move integration test product",
                new BigDecimal(price), "test", categoryId);
    }
}
