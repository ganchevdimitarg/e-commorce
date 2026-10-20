package com.ganchevdimitarg.catalog.product;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import com.ganchevdimitarg.catalog.dto.product.ProductResponseDto;
import com.ganchevdimitarg.catalog.dto.product.ProductSearchCriteria;
import com.ganchevdimitarg.catalog.exception.NotFoundException;
import com.ganchevdimitarg.catalog.service.product.ProductService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Search integration tests. Every fixture is scoped to a random run token so the shared
 * singleton Postgres container can carry rows seeded by other IT classes; assertions are
 * made against the seeded ids rather than global counts unless the applied filters make
 * the result uniquely part of this run.
 */
@Tag("integration")
@WithMockUser(authorities = "SCOPE_catalog.read")
class ProductSearchIT extends AbstractIntegrationTest {

    private static final BigDecimal MOUSE_PRICE = new BigDecimal("3107.11");
    private static final BigDecimal KEYBOARD_PRICE = new BigDecimal("3107.22");
    private static final BigDecimal PERCENT_PRICE = new BigDecimal("3107.33");
    private static final BigDecimal PERCENT_TRAP_PRICE = new BigDecimal("3107.44");
    private static final BigDecimal UNDERSCORE_PRICE = new BigDecimal("3107.55");
    private static final BigDecimal UNDERSCORE_TRAP_PRICE = new BigDecimal("3107.66");
    private static final BigDecimal PRICE_PRICE = new BigDecimal("3107.77");

    @Autowired
    ProductService productService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private String run;
    private String pcCategoryName;
    private String foodCategoryName;
    private UUID mouseId;
    private UUID keyboardId;
    private UUID percentId;
    private UUID percentTrapId;
    private UUID underscoreId;
    private UUID underscoreTrapId;
    private UUID priceId;
    private final List<UUID> seededProductIds = new ArrayList<>();
    private final List<UUID> seededCategoryIds = new ArrayList<>();

    @BeforeEach
    void seedSearchCatalogue() {
        run = UUID.randomUUID().toString().substring(0, 8);
        pcCategoryName = "search-cat-pc-" + run;
        foodCategoryName = "search-cat-food-" + run;
        UUID pcCategoryId = insertCategory(pcCategoryName);
        UUID foodCategoryId = insertCategory(foodCategoryName);

        mouseId = insertProduct("search-" + run + "-mouse", MOUSE_PRICE, true, pcCategoryId);
        keyboardId = insertProduct("search-" + run + "-keyboard", KEYBOARD_PRICE, false, pcCategoryId);
        percentId = insertProduct("100% cotton " + run, PERCENT_PRICE, true, foodCategoryId);
        percentTrapId = insertProduct("100x cotton " + run, PERCENT_TRAP_PRICE, false, foodCategoryId);
        underscoreId = insertProduct("a_b-" + run, UNDERSCORE_PRICE, true, foodCategoryId);
        underscoreTrapId = insertProduct("axb-" + run, UNDERSCORE_TRAP_PRICE, false, foodCategoryId);
        priceId = insertProduct("search-" + run + "-price", PRICE_PRICE, true, pcCategoryId);
    }

    @AfterEach
    void cleanUpSeededRows() {
        // FK order: comments, products, categories. Nothing else references the seeded rows.
        for (UUID productId : seededProductIds) {
            jdbcTemplate.update("delete from comments where product_id = ?", productId);
            jdbcTemplate.update("delete from products where id = ?", productId);
        }
        for (UUID categoryId : seededCategoryIds) {
            jdbcTemplate.update("delete from categories where id = ?", categoryId);
        }
        seededProductIds.clear();
        seededCategoryIds.clear();
    }

    @Test
    void should_filterByNameContains_when_qProvided() {
        String query = ("search-" + run + "-MOUse").toUpperCase(Locale.ROOT);

        Page<ProductResponseDto> page = search(new ProductSearchCriteria(query, null, null, null, null), 100);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(idsOf(page)).containsExactly(mouseId);
    }

    @Test
    void should_ignoreBlankQ_when_qIsBlank() {
        Page<ProductResponseDto> blankQ = productService.searchProducts(
                new ProductSearchCriteria("   ", null, null, null, pcCategoryName), PageRequest.of(0, 20));
        Page<ProductResponseDto> absentQ = productService.searchProducts(
                new ProductSearchCriteria(null, null, null, null, pcCategoryName), PageRequest.of(0, 20));

        assertThat(idsOf(blankQ)).containsExactlyInAnyOrderElementsOf(idsOf(absentQ));
        assertThat(idsOf(blankQ)).containsExactlyInAnyOrder(mouseId, keyboardId, priceId);
        assertThat(blankQ.getTotalElements()).isEqualTo(absentQ.getTotalElements());
    }

    @Test
    void should_treatPercentAndUnderscoreLiterally_when_qContainsThem() {
        Page<ProductResponseDto> percentPage =
                search(new ProductSearchCriteria("100% cotton " + run, null, null, null, null), 100);
        assertThat(idsOf(percentPage)).containsExactly(percentId);

        Page<ProductResponseDto> underscorePage =
                search(new ProductSearchCriteria("a_b-" + run, null, null, null, null), 100);
        assertThat(idsOf(underscorePage)).containsExactly(underscoreId);
    }

    @Test
    void should_filterByInclusivePriceRange_when_minAndMaxProvided() {
        Page<ProductResponseDto> page = search(
                new ProductSearchCriteria(null, MOUSE_PRICE, KEYBOARD_PRICE, null, null), 500);

        assertThat(idsOf(page))
                .contains(mouseId, keyboardId)
                .doesNotContain(percentId, percentTrapId, underscoreId, underscoreTrapId, priceId);
    }

    @Test
    void should_filterByStock_when_inStockProvided() {
        List<UUID> inStock = idsOf(search(new ProductSearchCriteria(null, null, null, true, null), 500));
        assertThat(inStock)
                .contains(mouseId, percentId, underscoreId, priceId)
                .doesNotContain(keyboardId, percentTrapId, underscoreTrapId);

        List<UUID> outOfStock = idsOf(search(new ProductSearchCriteria(null, null, null, false, null), 500));
        assertThat(outOfStock)
                .contains(keyboardId, percentTrapId, underscoreTrapId)
                .doesNotContain(mouseId);

        List<UUID> both = idsOf(search(new ProductSearchCriteria(run, null, null, null, null), 100));
        assertThat(both).contains(mouseId, keyboardId);
    }

    @Test
    void should_filterByCategory_when_categoryNameProvided() {
        Page<ProductResponseDto> page =
                search(new ProductSearchCriteria(null, null, null, null, pcCategoryName), 500);

        assertThat(idsOf(page)).containsExactlyInAnyOrder(mouseId, keyboardId, priceId);
    }

    @Test
    void should_combineAllFilters_when_allProvided() {
        Page<ProductResponseDto> page = search(new ProductSearchCriteria(
                "search-" + run, MOUSE_PRICE, PRICE_PRICE, true, pcCategoryName), 500);

        assertThat(idsOf(page)).containsExactlyInAnyOrder(mouseId, priceId);
    }

    @Test
    void should_returnEmptyPage_when_noProductMatches() {
        Page<ProductResponseDto> page =
                search(new ProductSearchCriteria("no-such-product-" + run, null, null, null, null), 100);

        assertThat(page.getTotalElements()).isZero();
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void should_return404_when_categoryNameIsUnknown() {
        ProductSearchCriteria criteria =
                new ProductSearchCriteria(null, null, null, null, "missing-cat-" + run);

        assertThatThrownBy(() -> search(criteria, 100))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Category not found: missing-cat-" + run);
    }

    @Test
    void should_paginateFilteredResults_when_pageRequested() {
        Page<ProductResponseDto> page = productService.searchProducts(
                new ProductSearchCriteria(run, null, null, null, null), PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isEqualTo(7);
        assertThat(page.getTotalPages()).isEqualTo(4);
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void should_boundStatements_when_searchPageRequested() {
        Statistics statistics = statistics();

        productService.searchProducts(new ProductSearchCriteria(null, null, null, null, null),
                PageRequest.of(0, 20));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    private Page<ProductResponseDto> search(ProductSearchCriteria criteria, int pageSize) {
        return productService.searchProducts(criteria, PageRequest.of(0, pageSize));
    }

    private static List<UUID> idsOf(Page<ProductResponseDto> page) {
        return page.getContent().stream().map(ProductResponseDto::id).toList();
    }

    private UUID insertCategory(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into categories (id, name, version) values (?, ?, 0)", id, name);
        seededCategoryIds.add(id);
        return id;
    }

    private UUID insertProduct(String name, BigDecimal price, boolean inStock, UUID categoryId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into products (id, name, description, price, stock, category_id, version)
                values (?, ?, ?, ?, ?, ?, 0)
                """, id, name, "Seeded product for search tests", price, inStock, categoryId);
        seededProductIds.add(id);
        return id;
    }

    private Statistics statistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        assertThat(statistics.isStatisticsEnabled())
                .as("hibernate.generate_statistics must be enabled in the test profile; "
                        + "otherwise every statement-count bound passes vacuously")
                .isTrue();
        statistics.clear();
        return statistics;
    }
}
