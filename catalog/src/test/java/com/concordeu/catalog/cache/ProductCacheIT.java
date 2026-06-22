package com.concordeu.catalog.cache;

import com.concordeu.catalog.AbstractIntegrationTest;
import com.concordeu.catalog.dto.product.ProductResponseDto;
import com.concordeu.catalog.event.ProductEventPublisher;
import com.concordeu.catalog.repository.ProductRepository;
import com.concordeu.catalog.service.product.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;

import java.math.BigDecimal;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductCacheIT extends AbstractIntegrationTest {

    private static final String CATEGORY_ID = "cache-cat-id";
    private static final String PRODUCT_ID = "cache-prod-id";
    private static final String PRODUCT_NAME = "cache-test-product";

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.cache.type", () -> "redis");
    }

    @MockitoBean
    ProductEventPublisher productEventPublisher;

    @MockitoSpyBean
    ProductRepository productRepository;

    @Autowired
    ProductService productService;

    @Autowired
    CacheManager cacheManager;

    @Autowired
    RedisConnectionFactory redisConnectionFactory;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        // Flush ALL Redis keys to guarantee a clean cache
        Objects.requireNonNull(redisConnectionFactory.getConnection()).serverCommands().flushAll();

        // Ensure the product exists (undo any soft-delete from a prior test)
        jdbc.update("""
                INSERT INTO categories (id, name, created_at, updated_at, version)
                VALUES (?, 'cache-test-cat', now(), now(), 0)
                ON CONFLICT (id) DO NOTHING
                """, CATEGORY_ID);

        jdbc.update("""
                INSERT INTO products (id, name, description, price, stock, characteristics,
                                      category_id, created_at, updated_at, version, deleted_at)
                VALUES (?, ?, 'A product for cache testing', ?, true, 'test-chars',
                        ?, now(), now(), 0, NULL)
                ON CONFLICT (id) DO UPDATE SET deleted_at = NULL,
                                               description = 'A product for cache testing',
                                               price = ?,
                                               stock = true,
                                               characteristics = 'test-chars',
                                               version = 0
                """, PRODUCT_ID, PRODUCT_NAME, BigDecimal.TEN, CATEGORY_ID, BigDecimal.TEN);

        // Clear spy invocation history so setUp calls are not counted
        clearInvocations(productRepository);
    }

    @Test
    @Order(1)
    @WithMockUser(authorities = "SCOPE_catalog.read")
    void should_returnCachedProduct_when_calledTwice() {
        // First call: cache miss, hits the repository
        ProductResponseDto first = productService.getProductById(PRODUCT_ID);
        assertThat(first).isNotNull();
        assertThat(first.name()).isEqualTo(PRODUCT_NAME);

        // Second call: cache hit, should NOT hit the repository
        ProductResponseDto second = productService.getProductById(PRODUCT_ID);
        assertThat(second).isNotNull();
        assertThat(second.name()).isEqualTo(PRODUCT_NAME);

        // Repository findById should have been called exactly once (first call only)
        verify(productRepository, times(1)).findById(PRODUCT_ID);
    }

    @Test
    @Order(2)
    @WithMockUser(authorities = {"SCOPE_catalog.read", "SCOPE_catalog.write"})
    void should_evictCache_when_productUpdated() {
        // Populate cache
        productService.getProductById(PRODUCT_ID);
        verify(productRepository, times(1)).findById(PRODUCT_ID);

        // Update product: evicts the cache after method completes successfully
        ProductResponseDto updateDto = new ProductResponseDto(
                null, null, "Updated description!", BigDecimal.ONE, true, "new-chars", null, null);
        productService.updateProduct(updateDto, PRODUCT_NAME);

        // Clear invocation count so we can assert fresh
        clearInvocations(productRepository);

        // Next read should miss the cache and hit the repository again
        ProductResponseDto afterUpdate = productService.getProductById(PRODUCT_ID);
        assertThat(afterUpdate).isNotNull();

        verify(productRepository, times(1)).findById(PRODUCT_ID);
    }

    @Test
    @Order(3)
    @WithMockUser(authorities = {"SCOPE_catalog.read", "SCOPE_catalog.write"})
    void should_evictCache_when_productDeleted() {
        // Populate cache
        productService.getProductById(PRODUCT_ID);
        verify(productRepository, times(1)).findById(PRODUCT_ID);

        // Delete product: evicts the cache after method completes successfully
        productService.deleteProduct(PRODUCT_NAME);

        // Verify cache is empty after eviction
        assertThat(cacheManager.getCache("product").get(PRODUCT_ID)).isNull();
    }
}
