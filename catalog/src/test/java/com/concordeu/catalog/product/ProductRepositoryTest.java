package com.concordeu.catalog.product;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@DataJpaTest
@Tag("integration")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository testRepository;

    @Test
    void updateShouldChangedParameters() {
        String productName = "aaaaa";
        Product product = Product.builder()
                .name(productName)
                .description("aaaaaaaaaaa")
                .price(BigDecimal.valueOf(0.01))
                .characteristics("aaaaaaa")
                .inStock(true)
                .build();

        testRepository.saveAndFlush(product);

        String productNewDescription = "ttttttttttttttt";

        testRepository.update(productName, productNewDescription, BigDecimal.valueOf(0.21), productNewDescription, true);

        Product productUpdate = testRepository.findByName(productName).get();

        assertThat(productUpdate.getDescription()).isEqualTo(productNewDescription);
    }
}