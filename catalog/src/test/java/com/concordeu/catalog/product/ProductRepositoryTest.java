package com.concordeu.catalog.product;

import com.concordeu.catalog.category.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@DataJpaTest
class ProductRepositoryTest {

    @Autowired
    private ProductRepository testRepository;

    @Test
    void saveProductInDB() {
        Product product = Product.builder()
                .name("mouse")
                .description("WiFi USB mouse")
                .price(BigDecimal.valueOf(32.12))
                .characteristics("Black and Red")
                .inStock(true)
                .category(Category.builder().name("PC").build())
                .comment(new ArrayList<>())
                .build();
        testRepository.saveAndFlush(product);

        boolean expected = testRepository.findByName("mouse").isPresent();

        assertThat(expected).isTrue();
    }

    @Test
    void findByName() {
    }

    @Test
    void findAllByCategoryOrderByNameAsc() {
    }
}