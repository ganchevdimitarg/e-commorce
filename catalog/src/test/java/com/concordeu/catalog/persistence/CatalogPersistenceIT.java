package com.concordeu.catalog.persistence;

import com.concordeu.catalog.AbstractIntegrationTest;
import com.concordeu.catalog.domain.Category;
import com.concordeu.catalog.repository.CategoryRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class CatalogPersistenceIT extends AbstractIntegrationTest {

    @Autowired
    CategoryRepository categoryRepository;

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
}
