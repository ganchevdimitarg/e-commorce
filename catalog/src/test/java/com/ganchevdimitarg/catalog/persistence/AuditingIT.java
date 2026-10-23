package com.ganchevdimitarg.catalog.persistence;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import com.ganchevdimitarg.catalog.cache.ProductCache;
import com.ganchevdimitarg.catalog.domain.Category;
import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.dto.category.CategoryResponseDto;
import com.ganchevdimitarg.catalog.dto.category.CreateCategoryCommand;
import com.ganchevdimitarg.catalog.dto.product.CreateProductCommand;
import com.ganchevdimitarg.catalog.dto.product.ProductResponseDto;
import com.ganchevdimitarg.catalog.dto.product.UpdateProductCommand;
import com.ganchevdimitarg.catalog.repository.CategoryRepository;
import com.ganchevdimitarg.catalog.repository.ProductRepository;
import com.ganchevdimitarg.catalog.service.category.CategoryService;
import com.ganchevdimitarg.catalog.service.product.ProductService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@code created_by}/{@code updated_by} are stamped from the security
 * principal on entity-managed writes and from the explicit auditor on bulk JPQL updates.
 *
 * <p>Assertions go through {@link JdbcTemplate} because the repositories hide
 * soft-deleted rows and the bulk update bypasses the persistence context. {@link ProductCache}
 * is mocked so the after-commit cache write does not attempt a Redis connection in this
 * Postgres-only context.
 *
 * <p>Every test runs in a transaction that is rolled back, so the shared Postgres
 * container is left untouched — in particular, no committed outbox rows leak into
 * {@code OutboxEventRepositoryIT}, which counts unsent rows globally.
 */
@Tag("integration")
@Transactional
class AuditingIT extends AbstractIntegrationTest {

    private static final String PRINCIPAL = "alice";
    private static final String BULK_AUDITOR = "bob";
    private static final String SYSTEM = "system";

    @MockitoBean
    ProductCache productCache;

    @Autowired
    CategoryService categoryService;

    @Autowired
    ProductService productService;

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_stampPrincipal_when_productCreatedAndUpdatedThroughServices() {
        authenticateAs(PRINCIPAL);

        String categoryName = "audit-cat-" + UUID.randomUUID();
        CategoryResponseDto category = categoryService.createCategory(new CreateCategoryCommand(categoryName));
        ProductResponseDto product = productService.createProduct(new CreateProductCommand(
                "audit-product-" + UUID.randomUUID().toString().substring(0, 8),
                "Product created by an audited principal",
                new BigDecimal("9.99"), true, "audit characteristics", categoryName));

        assertThat(productAuditColumns(product.id())).isEqualTo(Arrays.asList(PRINCIPAL, PRINCIPAL));
        assertThat(categoryAuditColumns(category.id())).isEqualTo(Arrays.asList(PRINCIPAL, PRINCIPAL));

        productService.updateProduct(product.id(),
                new UpdateProductCommand("Description updated by an audited principal",
                        new BigDecimal("19.99"), false, "updated characteristics"),
                null);

        // The update must not rewrite the creator, and the principal stamps updated_by.
        assertThat(productAuditColumns(product.id())).isEqualTo(Arrays.asList(PRINCIPAL, PRINCIPAL));
    }

    @Test
    void should_setExplicitAuditor_when_bulkUpdateByIdRuns() {
        SecurityContextHolder.clearContext();

        Category category = new Category();
        category.setName("audit-bulk-cat-" + UUID.randomUUID());
        categoryRepository.saveAndFlush(category);
        Product product = productRepository.saveAndFlush(newProduct(category));

        int updated = productRepository.updateById(product.getId(),
                "Description written by a bulk update", new BigDecimal("5.00"),
                "bulk characteristics", true, product.getVersion(), BULK_AUDITOR);

        assertThat(updated).isEqualTo(1);
        assertThat(productAuditColumns(product.getId())).isEqualTo(Arrays.asList(SYSTEM, BULK_AUDITOR));
    }

    @Test
    void should_recordSystem_when_noAuthenticationIsPresent() {
        SecurityContextHolder.clearContext();

        Category category = new Category();
        category.setName("audit-system-cat-" + UUID.randomUUID());

        Category saved = categoryRepository.saveAndFlush(category);

        assertThat(categoryAuditColumns(saved.getId())).isEqualTo(Arrays.asList(SYSTEM, SYSTEM));
    }

    private static void authenticateAs(String principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a",
                        List.of(new SimpleGrantedAuthority("SCOPE_catalog.manage"))));
    }

    private static Product newProduct(Category category) {
        Product product = new Product();
        product.setName("audit-bulk-" + UUID.randomUUID().toString().substring(0, 8));
        product.setDescription("Product seeded for the bulk audit test");
        product.setPrice(new BigDecimal("3.00"));
        product.setInStock(true);
        product.setCharacteristics("seeded characteristics");
        product.setCategory(category);
        return product;
    }

    private List<String> productAuditColumns(UUID id) {
        return jdbcTemplate.queryForObject(
                "select created_by, updated_by from products where id = ?",
                (rs, rowNum) -> Arrays.asList(rs.getString("created_by"), rs.getString("updated_by")), id);
    }

    private List<String> categoryAuditColumns(UUID id) {
        return jdbcTemplate.queryForObject(
                "select created_by, updated_by from categories where id = ?",
                (rs, rowNum) -> Arrays.asList(rs.getString("created_by"), rs.getString("updated_by")), id);
    }
}
