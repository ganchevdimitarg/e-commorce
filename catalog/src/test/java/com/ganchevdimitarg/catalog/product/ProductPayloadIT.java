package com.ganchevdimitarg.catalog.product;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import com.ganchevdimitarg.catalog.dto.product.ProductResponseDto;
import com.ganchevdimitarg.catalog.dto.product.ProductSearchCriteria;
import com.ganchevdimitarg.catalog.service.product.ProductService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A product with many reviews must not make the product read path expensive. The rating
 * summary and the paginated comment endpoint already carry what an embedded collection
 * used to provide.
 */
@Tag("integration")
@WithMockUser(authorities = "SCOPE_catalog.read")
class ProductPayloadIT extends AbstractIntegrationTest {

    private static final int COMMENTS = 2_000;

    @Autowired
    ProductService productService;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private UUID categoryId;
    private UUID productId;
    private String categoryName;

    @BeforeEach
    void seedProductWithManyComments() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        categoryName = "payload-cat-" + run;
        categoryId = UUID.randomUUID();
        productId = UUID.randomUUID();
        jdbc.update("insert into categories (id, name, version) values (?, ?, 0)",
                categoryId, categoryName);
        jdbc.update("""
                insert into products (id, name, description, price, stock, category_id, version)
                values (?, ?, ?, ?, true, ?, 0)
                """, productId, "payload-product-" + run,
                "Seeded product with many comments", BigDecimal.TEN, categoryId);

        List<Object[]> comments = new ArrayList<>(COMMENTS);
        for (int i = 0; i < COMMENTS; i++) {
            // Distinct authors: the live (product_id, author) unique index forbids reuse.
            comments.add(new Object[]{UUID.randomUUID(), "payload-reviewer-" + i, productId});
        }
        jdbc.batchUpdate("""
                insert into comments (id, title, text, star, author, product_id, version)
                values (?, 'payload title', 'Seeded payload comment text', 5, ?, ?, 0)
                """, comments);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from comments where product_id = ?", productId);
        jdbc.update("delete from products where id = ?", productId);
        jdbc.update("delete from categories where id = ?", categoryId);
    }

    @Test
    void should_readProductWithoutComments_when_productHasTwoThousandComments() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        assertThat(statistics.isStatisticsEnabled())
                .as("hibernate.generate_statistics must be enabled in the test profile")
                .isTrue();
        statistics.clear();

        List<ProductResponseDto> content = productService.searchProducts(
                        new ProductSearchCriteria(null, null, null, null, categoryName),
                        PageRequest.of(0, 20))
                .getContent();

        assertThat(content).singleElement()
                .satisfies(product -> assertThat(product.id()).isEqualTo(productId));
        assertThat(statistics.getPrepareStatementCount())
                .as("category lookup + page select; the page is not full, so no count query, "
                        + "and a comments batch select would add one")
                .isLessThanOrEqualTo(2);
    }
}
