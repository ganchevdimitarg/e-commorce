package com.concordeu.catalog.product;

import com.concordeu.catalog.dao.ProductDao;
import com.concordeu.catalog.domain.Product;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Tag("integration")
class ProductDaoTest {

    @Autowired
    private ProductDao testRepository;

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