package com.ganchevdimitarg.catalog.cache;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import com.ganchevdimitarg.catalog.dto.product.ProductResponseDto;
import com.ganchevdimitarg.catalog.dto.product.UpdateProductCommand;
import com.ganchevdimitarg.catalog.event.OutboxEventWriter;
import com.ganchevdimitarg.catalog.service.product.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A Redis outage must not fail requests: reads fall through to the database and writes
 * commit with the cache update skipped. Deliberately no Redis container — the properties
 * point at a closed port so every cache operation fails.
 */
@Tag("integration")
class CacheFailureIT extends AbstractIntegrationTest {

    private static final UUID CATEGORY_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000cf11");
    private static final UUID PRODUCT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000f00d");
    private static final String CATEGORY_NAME = "cache-failure-cat";
    private static final String PRODUCT_NAME = "cache-fail-product";

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> 1);           // closed port
        registry.add("spring.data.redis.timeout", () -> "200ms");
        registry.add("spring.data.redis.connect-timeout", () -> "200ms");
        registry.add("spring.cache.type", () -> "redis");
    }

    @MockitoBean
    OutboxEventWriter outboxEventWriter;

    @Autowired
    ProductService productService;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("""
                INSERT INTO categories (id, name, created_at, updated_at, version)
                VALUES (?, ?, now(), now(), 0)
                ON CONFLICT (id) DO NOTHING
                """, CATEGORY_ID, CATEGORY_NAME);

        // Ensure the product exists and is live (undo the soft-delete from the delete test)
        jdbc.update("""
                INSERT INTO products (id, name, description, price, stock, characteristics,
                                      category_id, created_at, updated_at, version, deleted_at)
                VALUES (?, ?, 'A product for cache failure testing', ?, true, 'test-chars',
                        ?, now(), now(), 0, NULL)
                ON CONFLICT (id) DO UPDATE SET deleted_at = NULL,
                                               category_id = ?,
                                               description = 'A product for cache failure testing',
                                               price = ?,
                                               stock = true,
                                               characteristics = 'test-chars',
                                               version = 0
                """, PRODUCT_ID, PRODUCT_NAME, BigDecimal.TEN, CATEGORY_ID,
                CATEGORY_ID, BigDecimal.TEN);
    }

    @Test
    @WithMockUser(authorities = "SCOPE_catalog.read")
    void should_serveReadFromDatabase_when_redisIsDown() {
        ProductResponseDto product = productService.getProductById(PRODUCT_ID);

        assertThat(product).isNotNull();
        assertThat(product.name()).isEqualTo(PRODUCT_NAME);
        assertThat(product.price()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_catalog.read", "SCOPE_catalog.manage"})
    void should_commitUpdate_when_redisIsDown() {
        UpdateProductCommand command = new UpdateProductCommand(
                "Updated while the cache is down", BigDecimal.valueOf(42), true, "test-chars");

        ProductResponseDto updated = productService.updateProduct(PRODUCT_ID, command, null);

        assertThat(updated.price()).isEqualByComparingTo(BigDecimal.valueOf(42));
        BigDecimal storedPrice = jdbc.queryForObject(
                "SELECT price FROM products WHERE id = ?", BigDecimal.class, PRODUCT_ID);
        assertThat(storedPrice).isEqualByComparingTo(BigDecimal.valueOf(42));
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_catalog.read", "SCOPE_catalog.manage"})
    void should_commitDelete_when_redisIsDown() {
        productService.deleteProduct(PRODUCT_ID);

        Integer deleted = jdbc.queryForObject(
                "SELECT COUNT(*) FROM products WHERE id = ? AND deleted_at IS NOT NULL",
                Integer.class, PRODUCT_ID);
        assertThat(deleted).isEqualTo(1);
    }
}
