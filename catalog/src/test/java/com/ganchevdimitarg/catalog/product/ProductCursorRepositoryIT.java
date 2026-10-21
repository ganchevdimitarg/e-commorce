package com.ganchevdimitarg.catalog.product;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.dto.product.CursorPageResponse;
import com.ganchevdimitarg.catalog.dto.product.ProductResponseDto;
import com.ganchevdimitarg.catalog.dto.product.ProductSearchCriteria;
import com.ganchevdimitarg.catalog.exception.ValidationException;
import com.ganchevdimitarg.catalog.repository.ProductCursorRepository;
import com.ganchevdimitarg.catalog.service.product.ProductService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cursor (keyset) pagination integration tests. The 25 live products and the soft-deleted
 * trap are scoped to a random run token so the shared singleton Postgres container can carry
 * rows seeded by other IT classes.
 */
@Tag("integration")
@WithMockUser(authorities = "SCOPE_catalog.read")
class ProductCursorRepositoryIT extends AbstractIntegrationTest {

    private static final int LIVE_PRODUCTS = 25;
    private static final int PAGE_SIZE = 10;
    private static final Instant BASE = Instant.parse("2026-03-01T00:00:00Z");

    @Autowired
    ProductService productService;

    @Autowired
    ProductCursorRepository productCursorRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private String run;
    private String categoryName;
    private UUID categoryId;
    private UUID softDeletedId;
    private final List<UUID> seededProductIds = new ArrayList<>();
    private final Map<UUID, Instant> createdAtById = new HashMap<>();

    @BeforeEach
    void seedCatalogue() {
        run = UUID.randomUUID().toString().substring(0, 8);
        categoryName = "cursor-cat-" + run;
        categoryId = UUID.randomUUID();
        jdbcTemplate.update("insert into categories (id, name, version) values (?, ?, 0)",
                categoryId, categoryName);

        for (int i = 0; i < LIVE_PRODUCTS; i++) {
            UUID id = UUID.randomUUID();
            Instant createdAt = createdAtFor(i);
            insertProduct(id, "cursor-" + run + "-" + String.format("%02d", i), createdAt, null);
            seededProductIds.add(id);
            createdAtById.put(id, createdAt);
        }

        // Shares a createdAt with a live product on purpose: the soft-delete restriction,
        // not the keyset, must keep it out.
        softDeletedId = UUID.randomUUID();
        insertProduct(softDeletedId, "cursor-" + run + "-deleted", createdAtFor(3), Instant.now());
    }

    @AfterEach
    void cleanUpSeededRows() {
        jdbcTemplate.update("delete from comments where product_id in (select id from products where category_id = ?)",
                categoryId);
        jdbcTemplate.update("delete from products where category_id = ?", categoryId);
        jdbcTemplate.update("delete from categories where id = ?", categoryId);
        seededProductIds.clear();
        createdAtById.clear();
    }

    @Test
    void should_pageThroughAllProductsWithoutDuplicatesOrGaps_when_ascending() {
        List<CursorPageResponse<ProductResponseDto>> pages = fetchAllPages(criteria(null), "asc");

        assertThat(pages).hasSize(3);
        assertThat(pages.get(0).content()).hasSize(10);
        assertThat(pages.get(1).content()).hasSize(10);
        assertThat(pages.get(2).content()).hasSize(5);
        assertThat(pages.get(0).nextCursor()).isNotNull();
        assertThat(pages.get(1).nextCursor()).isNotNull();
        assertThat(pages.get(2).nextCursor()).isNull();

        List<UUID> ids = idsOf(pages);
        assertThat(ids)
                .doesNotHaveDuplicates()
                .hasSize(LIVE_PRODUCTS)
                .containsExactlyInAnyOrderElementsOf(seededProductIds)
                .doesNotContain(softDeletedId);

        List<Instant> createdAts = ids.stream().map(createdAtById::get).toList();
        assertThat(createdAts).isSorted();
        assertTiedCreatedAtProductsAreAdjacent(ids);
    }

    @Test
    void should_returnExactlyReversedOrder_when_descending() {
        List<UUID> ascending = idsOf(fetchAllPages(criteria(null), "asc"));
        List<UUID> descending = idsOf(fetchAllPages(criteria(null), "desc"));

        assertThat(descending).containsExactlyElementsOf(ascending.reversed());
    }

    @Test
    void should_keepFiltersAcrossPages_when_qAndCategoryProvided() {
        ProductSearchCriteria filtered =
                new ProductSearchCriteria("cursor-" + run, null, null, null, categoryName);

        List<UUID> ids = idsOf(fetchAllPages(filtered, "desc"));

        assertThat(ids).hasSize(LIVE_PRODUCTS)
                .containsExactlyInAnyOrderElementsOf(seededProductIds);
    }

    @Test
    void should_excludeSoftDeletedProducts_when_paging() {
        List<UUID> ids = idsOf(fetchAllPages(criteria(null), "asc"));

        assertThat(ids).doesNotContain(softDeletedId);
    }

    @Test
    void should_returnAtMostLimit_when_repositoryPageRequested() {
        List<Product> page = productCursorRepository.findPage(
                criteria(null), categoryId, null, "asc", 4);

        assertThat(page).hasSize(4);
        assertThat(page).extracting(Product::getId).doesNotContain(softDeletedId);
        assertThat(page).allSatisfy(product -> assertThat(product.getCategory()).isNotNull());
    }

    @Test
    void should_rejectUnsupportedSort_when_serviceCalledWithPriceSort() {
        assertThatThrownBy(() -> productService.searchProductsByCursor(criteria(null), null, 10, "price,asc"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void should_notIssueCountQuery_when_cursorPageRequested() {
        ProductSearchCriteria filtered =
                new ProductSearchCriteria("cursor-" + run, null, null, null, null);
        CursorPageResponse<ProductResponseDto> firstPage =
                productService.searchProductsByCursor(filtered, null, PAGE_SIZE, "desc");
        assertThat(firstPage.nextCursor()).isNotNull();

        Statistics statistics = statistics();
        productService.searchProductsByCursor(filtered, firstPage.nextCursor(), PAGE_SIZE, "desc");

        assertThat(statistics.getPrepareStatementCount())
                .as("one page select plus one batched comments select; a count query would add a statement")
                .isLessThanOrEqualTo(2);
        assertThat(statistics.getQueries())
                .as("statistics must record the executed queries for this assertion to mean anything")
                .isNotEmpty()
                .noneMatch(query -> query.toLowerCase(Locale.ROOT).contains("count("));
    }

    private ProductSearchCriteria criteria(String q) {
        return new ProductSearchCriteria(q, null, null, null, categoryName);
    }

    private List<CursorPageResponse<ProductResponseDto>> fetchAllPages(
            ProductSearchCriteria criteria, String direction) {
        List<CursorPageResponse<ProductResponseDto>> pages = new ArrayList<>();
        String cursor = null;
        for (int pageNumber = 0; pageNumber < 10; pageNumber++) {
            CursorPageResponse<ProductResponseDto> page =
                    productService.searchProductsByCursor(criteria, cursor, PAGE_SIZE, direction);
            pages.add(page);
            cursor = page.nextCursor();
            if (cursor == null) {
                return pages;
            }
        }
        return pages;
    }

    private static List<UUID> idsOf(List<CursorPageResponse<ProductResponseDto>> pages) {
        return pages.stream()
                .flatMap(page -> page.content().stream())
                .map(ProductResponseDto::id)
                .toList();
    }

    private void assertTiedCreatedAtProductsAreAdjacent(List<UUID> ids) {
        List<UUID> tied = ids.stream()
                .filter(id -> createdAtById.get(id).equals(createdAtFor(10)))
                .toList();
        assertThat(tied).as("products 10 and 11 share createdAt").hasSize(2);
        assertThat(Math.abs(ids.indexOf(tied.get(0)) - ids.indexOf(tied.get(1))))
                .as("the id tie-break keeps equal createdAt values adjacent")
                .isEqualTo(1);
    }

    private static Instant createdAtFor(int index) {
        // Products 10 and 11 share a timestamp so the (createdAt, id) tie-break is exercised.
        return BASE.plusSeconds(index == 11 ? 10 : index);
    }

    private void insertProduct(UUID id, String name, Instant createdAt, Instant deletedAt) {
        jdbcTemplate.update("""
                insert into products (id, name, description, price, stock, category_id, version,
                                      created_at, updated_at, deleted_at)
                values (?, ?, ?, ?, ?, ?, 0, ?, ?, ?)
                """, id, name, "Seeded product for cursor pagination", BigDecimal.TEN, true,
                categoryId, Timestamp.from(createdAt), Timestamp.from(createdAt),
                deletedAt == null ? null : Timestamp.from(deletedAt));
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
