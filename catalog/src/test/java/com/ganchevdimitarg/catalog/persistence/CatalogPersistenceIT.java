package com.ganchevdimitarg.catalog.persistence;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import com.ganchevdimitarg.catalog.domain.Category;
import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.repository.CategoryRepository;
import com.ganchevdimitarg.catalog.repository.ProductRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class CatalogPersistenceIT extends AbstractIntegrationTest {

    @Autowired
    CategoryRepository categoryRepository;

    @Autowired
    ProductRepository productRepository;

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
