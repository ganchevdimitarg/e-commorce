package com.ganchevdimitarg.catalog.persistence;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import com.ganchevdimitarg.catalog.dto.product.ProductSearchCriteria;
import com.ganchevdimitarg.catalog.service.category.CategoryService;
import com.ganchevdimitarg.catalog.service.comment.CommentService;
import com.ganchevdimitarg.catalog.service.product.ProductService;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@WithMockUser(username = "qc-author", authorities = "SCOPE_catalog.read")
class ListQueryCountIT extends AbstractIntegrationTest {

    @Autowired
    ProductService productService;

    @Autowired
    CategoryService categoryService;

    @Autowired
    CommentService commentService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    String categoryName;
    String productName;
    String author;

    @BeforeEach
    void seedCatalogue() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        // The service guard only lets the author (or an admin) list their comments, so the
        // seeded author must match the @WithMockUser principal.
        author = "qc-author";
        categoryName = null;
        productName = null;
        for (int c = 0; c < 5; c++) {
            UUID categoryId = UUID.randomUUID();
            String currentCategory = "qc-cat-" + run + "-" + c;
            if (categoryName == null) {
                categoryName = currentCategory;
            }
            jdbcTemplate.update("insert into categories (id, name, version) values (?, ?, 0)",
                    categoryId, currentCategory);
            for (int p = 0; p < 5; p++) {
                UUID productId = UUID.randomUUID();
                String name = "qc-" + run + "-" + c + p;
                if (productName == null) {
                    productName = name;
                }
                jdbcTemplate.update("""
                        insert into products (id, name, description, price, stock, category_id, version)
                        values (?, ?, ?, ?, ?, ?, 0)
                        """, productId, name, "Seeded product for query counts",
                        BigDecimal.TEN, true, categoryId);
                for (int i = 0; i < 2; i++) {
                    // At most one live comment per (product, author): the second seed uses
                    // a different author so the unique index is not violated.
                    String commentAuthor = i == 0 ? author : author + "-alt";
                    jdbcTemplate.update("""
                            insert into comments (id, title, text, star, author, product_id, version)
                            values (?, ?, ?, ?, ?, ?, 0)
                            """, UUID.randomUUID(), "qc title", "Seeded comment text", 5.0,
                            commentAuthor, productId);
                }
            }
        }
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

    @Test
    void should_boundStatements_when_productSearchRequested() {
        Statistics stats = statistics();

        productService.searchProducts(new ProductSearchCriteria(null, null, null, null, null),
                PageRequest.of(0, 20));

        assertThat(stats.getPrepareStatementCount())
                .as("count + page select; the comments batch is gone")
                .isLessThanOrEqualTo(2);
    }

    @Test
    void should_boundStatements_when_productSearchByCategoryRequested() {
        Statistics stats = statistics();

        productService.searchProducts(new ProductSearchCriteria(null, null, null, null, categoryName),
                PageRequest.of(0, 20));

        assertThat(stats.getPrepareStatementCount())
                .as("category lookup + page select; the page is not full, so no count query, "
                        + "and the comments batch is gone")
                .isLessThanOrEqualTo(2);
    }

    @Test
    void should_boundStatements_when_categoryPageRequested() {
        Statistics stats = statistics();

        categoryService.getCategoriesByPage(PageRequest.of(0, 20));

        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    @Test
    void should_boundStatements_when_commentsByAuthorPageRequested() {
        Statistics stats = statistics();

        commentService.findAllByAuthorByPage(author, PageRequest.of(0, 20));

        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(3);
    }

    @Test
    void should_boundStatements_when_commentsByProductPageRequested() {
        Statistics stats = statistics();

        commentService.findAllByProductNameByPage(productName, PageRequest.of(0, 20));

        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }
}
