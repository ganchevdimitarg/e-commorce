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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cursor (keyset) pagination integration tests. The 25 live products and the soft-deleted
 * trap are scoped to a random run token so the shared singleton Postgres container can carry
 * rows seeded by other IT classes. Two pairs of live products share a {@code createdAt} on
 * purpose — one straddling the first ascending page boundary and one the first descending
 * boundary — so the id tie-break of both cursor predicate directions is exercised.
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

    @BeforeEach
    void seedCatalogue() {
        run = UUID.randomUUID().toString().substring(0, 8);
        categoryName = "cursor-cat-" + run;
        categoryId = UUID.randomUUID();
        jdbcTemplate.update("insert into categories (id, name, version) values (?, ?, 0)",
                categoryId, categoryName);

        for (int i = 0; i < LIVE_PRODUCTS; i++) {
            UUID id = UUID.randomUUID();
            insertProduct(id, "cursor-" + run + "-" + String.format("%02d", i), createdAtFor(i), null);
            seededProductIds.add(id);
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
    }

    @Test
    void should_pageThroughAllProductsWithoutDuplicatesOrGaps_when_ascending() {
        List<CursorPageResponse<ProductResponseDto>> pages = fetchAllPages(criteria(null), "asc");
        List<UUID> expected = expectedIdsAscending();

        assertThat(pages).hasSize(3);
        assertThat(pages.get(0).content()).hasSize(10);
        assertThat(pages.get(1).content()).hasSize(10);
        assertThat(pages.get(2).content()).hasSize(5);
        assertThat(pages.get(0).nextCursor()).isNotNull();
        assertThat(pages.get(1).nextCursor()).isNotNull();
        assertThat(pages.get(2).nextCursor()).isNull();

        List<UUID> ids = idsOf(pages);
        assertThat(ids)
                .containsExactlyElementsOf(expected)
                .doesNotHaveDuplicates()
                .hasSize(LIVE_PRODUCTS)
                .doesNotContain(softDeletedId);
        assertTieStraddlesFirstPageBoundary(pages, expected);
    }

    @Test
    void should_returnExactlyReversedOrder_when_descending() {
        List<CursorPageResponse<ProductResponseDto>> ascendingPages = fetchAllPages(criteria(null), "asc");
        List<CursorPageResponse<ProductResponseDto>> descendingPages = fetchAllPages(criteria(null), "desc");
        List<UUID> ascending = idsOf(ascendingPages);
        List<UUID> descending = idsOf(descendingPages);

        assertThat(descending)
                .containsExactlyElementsOf(expectedIdsDescending())
                .containsExactlyElementsOf(ascending.reversed());
        assertTieStraddlesDescendingPageBoundary(descendingPages, expectedIdsDescending());
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
                .as("one page select; no count query and no comments batch query")
                .isLessThanOrEqualTo(1);
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

    /**
     * The single-shot ORDER BY is the oracle for the OR-expanded keyset predicate: a wrong
     * id comparison (or a missing tie-break) would reorder, duplicate or drop rows.
     */
    private List<UUID> expectedIdsAscending() {
        return jdbcTemplate.queryForList("""
                        select id from products
                        where category_id = ? and deleted_at is null
                        order by created_at asc, id asc
                        """, String.class, categoryId)
                .stream()
                .map(UUID::fromString)
                .toList();
    }

    private List<UUID> expectedIdsDescending() {
        return jdbcTemplate.queryForList("""
                        select id from products
                        where category_id = ? and deleted_at is null
                        order by created_at desc, id desc
                        """, String.class, categoryId)
                .stream()
                .map(UUID::fromString)
                .toList();
    }

    /**
     * Products 9 and 10 share a {@code createdAt} on the first page boundary, so page 2 can
     * only start with the second of the pair if the cursor predicate applies the id tie-break
     * with the right comparison.
     */
    private static void assertTieStraddlesFirstPageBoundary(
            List<CursorPageResponse<ProductResponseDto>> pages, List<UUID> expected) {
        assertThat(createdAtFor(9)).isEqualTo(createdAtFor(10));
        assertThat(pages.get(0).content().getLast().id()).isEqualTo(expected.get(9));
        assertThat(pages.get(1).content().getFirst().id()).isEqualTo(expected.get(10));
    }

    /**
     * Products 14 and 15 share a {@code createdAt} at ascending positions 14/15, which are
     * descending positions 9/10: the pair straddles the descending page boundary. This is the
     * only place where the descending tie-break branch is load-bearing.
     */
    private static void assertTieStraddlesDescendingPageBoundary(
            List<CursorPageResponse<ProductResponseDto>> pages, List<UUID> expectedDescending) {
        assertThat(createdAtFor(14)).isEqualTo(createdAtFor(15));
        assertThat(pages.get(0).content().getLast().id()).isEqualTo(expectedDescending.get(9));
        assertThat(pages.get(1).content().getFirst().id()).isEqualTo(expectedDescending.get(10));
    }

    private static Instant createdAtFor(int index) {
        // Two tied pairs, one per direction:
        //  - indices 9 and 10 share BASE+9s  -> ascending positions 9/10  (asc boundary)
        //  - indices 14 and 15 share BASE+14s -> descending positions 9/10 (desc boundary)
        if (index == 10) {
            return BASE.plusSeconds(9);
        }
        if (index == 15) {
            return BASE.plusSeconds(14);
        }
        return BASE.plusSeconds(index);
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
