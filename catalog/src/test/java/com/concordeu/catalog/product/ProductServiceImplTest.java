package com.concordeu.catalog.product;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class ProductServiceImplTest {

    ProductService testServer;

    @Mock
    ProductRepository mockRepository;



    @Test
    void createProduct_should_create_and_save_new_product() {

    }

    void getProducts() {
    }

    void updateProduct() {
    }

    void deleteProduct() {
    }
}