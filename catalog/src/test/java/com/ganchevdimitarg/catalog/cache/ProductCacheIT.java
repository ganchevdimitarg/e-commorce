package com.ganchevdimitarg.catalog.cache;

import com.ganchevdimitarg.catalog.RedisIntegrationBase;
import com.ganchevdimitarg.catalog.dto.category.MoveProductCommand;
import com.ganchevdimitarg.catalog.dto.comment.CommentResponseDto;
import com.ganchevdimitarg.catalog.dto.comment.CreateCommentCommand;
import com.ganchevdimitarg.catalog.dto.product.CreateProductCommand;
import com.ganchevdimitarg.catalog.dto.product.ProductResponseDto;
import com.ganchevdimitarg.catalog.dto.product.UpdateProductCommand;
import com.ganchevdimitarg.catalog.event.OutboxEventWriter;
import com.ganchevdimitarg.catalog.repository.ProductRepository;
import com.ganchevdimitarg.catalog.service.category.CategoryService;
import com.ganchevdimitarg.catalog.service.comment.CommentService;
import com.ganchevdimitarg.catalog.service.product.ProductService;
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

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Tag("integration")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProductCacheIT extends RedisIntegrationBase {

    private static final UUID CATEGORY_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000ca11");
    private static final UUID CATEGORY_2_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-00000000ca22");
    private static final UUID PRODUCT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-00000000d00d");
    private static final String CATEGORY_NAME = "cache-test-cat";
    private static final String CATEGORY_2_NAME = "cache-test-cat-2";
    private static final String PRODUCT_NAME = "cache-test-product";

    @DynamicPropertySource
    static void cacheProperties(DynamicPropertyRegistry registry) {
        // Redis host/port come from the shared RedisIntegrationBase container.
        registry.add("spring.cache.type", () -> "redis");
    }

    @MockitoBean
    OutboxEventWriter outboxEventWriter;

    @MockitoSpyBean
    ProductRepository productRepository;

    @Autowired
    ProductService productService;

    @Autowired
    CommentService commentService;

    @Autowired
    CategoryService categoryService;

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

        // Ensure both categories exist; the move tests need a destination category
        jdbc.update("""
                INSERT INTO categories (id, name, created_at, updated_at, version)
                VALUES (?, ?, now(), now(), 0)
                ON CONFLICT (id) DO NOTHING
                """, CATEGORY_ID, CATEGORY_NAME);

        jdbc.update("""
                INSERT INTO categories (id, name, created_at, updated_at, version)
                VALUES (?, ?, now(), now(), 0)
                ON CONFLICT (id) DO NOTHING
                """, CATEGORY_2_ID, CATEGORY_2_NAME);

        // Ensure the product exists (undo any soft-delete or move from a prior test)
        jdbc.update("""
                INSERT INTO products (id, name, description, price, stock, characteristics,
                                      category_id, created_at, updated_at, version, deleted_at)
                VALUES (?, ?, 'A product for cache testing', ?, true, 'test-chars',
                        ?, now(), now(), 0, NULL)
                ON CONFLICT (id) DO UPDATE SET deleted_at = NULL,
                                               category_id = ?,
                                               description = 'A product for cache testing',
                                               price = ?,
                                               stock = true,
                                               characteristics = 'test-chars',
                                               version = 0
                """, PRODUCT_ID, PRODUCT_NAME, BigDecimal.TEN, CATEGORY_ID,
                CATEGORY_ID, BigDecimal.TEN);

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
    @WithMockUser(authorities = {"SCOPE_catalog.read", "SCOPE_catalog.manage"})
    void should_serveUpdatedProductFromCache_when_readAfterUpdate() {
        // Populate cache
        productService.getProductById(PRODUCT_ID);
        verify(productRepository, times(1)).findById(PRODUCT_ID);

        // Update product: the refreshed DTO is put into the cache after commit
        UpdateProductCommand updateCmd = new UpdateProductCommand(
                "Updated description!", BigDecimal.ONE, true, "new-chars");
        productService.updateProduct(PRODUCT_ID, updateCmd, null);

        // Clear invocation count so we can assert fresh
        clearInvocations(productRepository);

        // Next read is served from the cache with the updated state
        ProductResponseDto afterUpdate = productService.getProductById(PRODUCT_ID);
        assertThat(afterUpdate).isNotNull();
        assertThat(afterUpdate.description()).isEqualTo("Updated description!");
        assertThat(afterUpdate.price()).isEqualByComparingTo(BigDecimal.ONE);

        verify(productRepository, never()).findById(any());
    }

    @Test
    @Order(3)
    @WithMockUser(authorities = {"SCOPE_catalog.read", "SCOPE_catalog.manage"})
    void should_evictCache_when_productDeleted() {
        // Populate cache
        productService.getProductById(PRODUCT_ID);
        verify(productRepository, times(1)).findById(PRODUCT_ID);

        // Delete product: evicts the cache after method completes successfully
        productService.deleteProduct(PRODUCT_ID);

        // Verify cache is empty after eviction
        assertThat(cacheManager.getCache("product").get(PRODUCT_ID)).isNull();
    }

    @Test
    @Order(4)
    @WithMockUser(authorities = {"SCOPE_catalog.read", "SCOPE_catalog.review"})
    void should_evictProduct_when_commentCreated() {
        productService.getProductById(PRODUCT_ID);
        assertThat(cacheManager.getCache("product").get(PRODUCT_ID)).isNotNull();

        commentService.createComment(new CreateCommentCommand(
                "great", "thorough review text", 5.0, "joe", PRODUCT_NAME));

        assertThat(cacheManager.getCache("product").get(PRODUCT_ID)).isNull();
    }

    @Test
    @Order(5)
    @WithMockUser(authorities = {"SCOPE_catalog.read", "SCOPE_catalog.manage"})
    void should_evictProduct_when_productMoved() {
        productService.getProductById(PRODUCT_ID);
        assertThat(cacheManager.getCache("product").get(PRODUCT_ID)).isNotNull();

        categoryService.moveOneProduct(
                new MoveProductCommand(CATEGORY_NAME, CATEGORY_2_NAME, PRODUCT_NAME));

        assertThat(cacheManager.getCache("product").get(PRODUCT_ID)).isNull();
    }

    @Test
    @Order(6)
    @WithMockUser(authorities = {"SCOPE_catalog.read", "SCOPE_catalog.manage"})
    void should_evictAllProducts_when_allProductsMoved() {
        productService.getProductById(PRODUCT_ID);
        assertThat(cacheManager.getCache("product").get(PRODUCT_ID)).isNotNull();

        categoryService.moveAllProducts(CATEGORY_NAME, CATEGORY_2_NAME);

        assertThat(cacheManager.getCache("product").get(PRODUCT_ID)).isNull();
    }

    @Test
    @Order(7)
    @WithMockUser(authorities = {"SCOPE_catalog.read", "SCOPE_catalog.manage"})
    void should_serveCreatedProductFromCache_when_readImmediatelyAfterCreate() {
        ProductResponseDto created = null;
        try {
            created = productService.createProduct(new CreateProductCommand(
                    "cache-new-product", "A product created for cache",
                    BigDecimal.TEN, true, "test-chars", CATEGORY_NAME));

            clearInvocations(productRepository);

            ProductResponseDto read = productService.getProductById(created.id());

            assertThat(read.name()).isEqualTo("cache-new-product");
            verify(productRepository, never()).findById(any());
        } finally {
            // Remove the created row so a rerun starts clean
            if (created != null) {
                jdbc.update("DELETE FROM products WHERE id = ?", created.id());
            }
        }
    }

    @Test
    @Order(8)
    @WithMockUser(authorities = {"SCOPE_catalog.read", "SCOPE_catalog.manage"})
    void should_serveUpdatedProductFromCache_when_readImmediatelyAfterUpdate() {
        productService.updateProduct(PRODUCT_ID, new UpdateProductCommand(
                "Price update for cache", BigDecimal.valueOf(42), true, "test-chars"), null);

        clearInvocations(productRepository);

        ProductResponseDto read = productService.getProductById(PRODUCT_ID);

        assertThat(read.price()).isEqualByComparingTo(BigDecimal.valueOf(42));
        verify(productRepository, never()).findById(any());
    }

    @Test
    @Order(9)
    @WithMockUser(username = "joe", authorities = {"SCOPE_catalog.read", "SCOPE_catalog.review"})
    void should_evictProduct_when_commentDeleted() {
        CommentResponseDto created = commentService.createComment(new CreateCommentCommand(
                "great", "thorough review text", 5.0, "joe", PRODUCT_NAME));

        productService.getProductById(PRODUCT_ID);
        assertThat(cacheManager.getCache("product").get(PRODUCT_ID)).isNotNull();

        commentService.deleteComment(created.id());

        assertThat(cacheManager.getCache("product").get(PRODUCT_ID)).isNull();
    }
}
