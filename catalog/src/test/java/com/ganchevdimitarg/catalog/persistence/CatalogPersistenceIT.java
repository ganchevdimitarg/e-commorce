package com.ganchevdimitarg.catalog.persistence;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import com.ganchevdimitarg.catalog.domain.Category;
import com.ganchevdimitarg.catalog.domain.Comment;
import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.repository.CategoryRepository;
import com.ganchevdimitarg.catalog.repository.CommentRepository;
import com.ganchevdimitarg.catalog.repository.ProductRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("integration")
class CatalogPersistenceIT extends AbstractIntegrationTest {

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    CommentRepository commentRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void should_populateAuditColumns_when_entityPersisted() {
        Category category = new Category();
        category.setName("integration-cat");

        Category saved = categoryRepository.saveAndFlush(category);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getDeletedAt()).isNull();
        assertThat(saved.getVersion()).isZero();
    }

    @Test
    @Transactional
    void should_hideRow_when_softDeleted() {
        Category category = new Category();
        category.setName("to-delete");
        categoryRepository.saveAndFlush(category);

        categoryRepository.deleteByName("to-delete");

        assertThat(categoryRepository.findByName("to-delete")).isEmpty();
    }

    @Test
    @Transactional
    void should_excludeSoftDeleted_when_findAllByCategoryIdByPage() {
        // given — a category with two products
        Category category = new Category();
        category.setName("soft-del-cat");
        categoryRepository.saveAndFlush(category);

        Product active = createProduct("active-prod", "Active product description", category);
        Product toDelete = createProduct("deleted-prod", "Deleted product description", category);
        productRepository.saveAndFlush(active);
        productRepository.saveAndFlush(toDelete);

        // soft-delete one product (triggers @SQLDelete)
        productRepository.delete(toDelete);
        entityManager.flush();
        entityManager.clear();

        // when
        Page<Product> result = productRepository.findAllByCategoryIdByPage(
                category.getId(), PageRequest.of(0, 10));

        // then — only the active product is returned
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent())
                .extracting(Product::getName)
                .containsExactly("active-prod");
    }

    @Test
    @Transactional
    void should_recreateName_when_previousRowWasSoftDeleted() {
        String name = "recreate-cat-" + UUID.randomUUID();
        Category first = new Category();
        first.setName(name);
        categoryRepository.saveAndFlush(first);

        categoryRepository.delete(first);
        entityManager.flush();

        Category second = new Category();
        second.setName(name);

        assertThatCode(() -> categoryRepository.saveAndFlush(second))
                .doesNotThrowAnyException();
    }

    @Test
    @Transactional
    void should_recreateProductName_when_previousProductWasSoftDeleted() {
        Category category = new Category();
        category.setName("recreate-prod-cat-" + UUID.randomUUID());
        categoryRepository.saveAndFlush(category);

        String name = "rc-" + UUID.randomUUID().toString().substring(0, 8);
        Product first = createProduct(name, "Product to soft-delete", category);
        productRepository.saveAndFlush(first);

        productRepository.delete(first);
        entityManager.flush();

        Product second = createProduct(name, "Product recreated after delete", category);

        assertThatCode(() -> productRepository.saveAndFlush(second))
                .doesNotThrowAnyException();
    }

    @Test
    void should_rejectDuplicateLiveName_when_bothRowsAreActive() {
        String name = "dup-cat-" + UUID.randomUUID();
        Category first = new Category();
        first.setName(name);
        categoryRepository.saveAndFlush(first);

        Category second = new Category();
        second.setName(name);

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_rejectDuplicateLiveProductName_when_bothRowsAreActive() {
        Category category = new Category();
        category.setName("dup-prod-cat-" + UUID.randomUUID());
        categoryRepository.saveAndFlush(category);

        String name = "dp-" + UUID.randomUUID().toString().substring(0, 8);
        productRepository.saveAndFlush(createProduct(name, "First live product", category));

        Product second = createProduct(name, "Second live product", category);

        assertThatThrownBy(() -> productRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_rejectOutOfRangeStar_when_commentPersistedDirectly() {
        Category category = new Category();
        category.setName("star-cat-" + UUID.randomUUID());
        categoryRepository.saveAndFlush(category);

        Product product = createProduct("sp-" + UUID.randomUUID().toString().substring(0, 8),
                "Star range product", category);
        productRepository.saveAndFlush(product);

        Comment comment = new Comment();
        comment.setTitle("bad star");
        comment.setText("Out of range rating");
        comment.setStar(6.0);
        comment.setAuthor("tester");
        comment.setProduct(product);

        assertThatThrownBy(() -> commentRepository.saveAndFlush(comment))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("comments_star_range");
    }

    private Product createProduct(String name, String description, Category category) {
        Product product = new Product();
        product.setName(name);
        product.setDescription(description);
        product.setPrice(BigDecimal.TEN);
        product.setInStock(true);
        product.setCharacteristics("test characteristics");
        product.setCategory(category);
        return product;
    }
}
