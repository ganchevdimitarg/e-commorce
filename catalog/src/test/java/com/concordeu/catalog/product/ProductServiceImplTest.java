package com.concordeu.catalog.product;


import com.concordeu.catalog.category.Category;
import com.concordeu.catalog.category.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    private ProductService testServer;
    @Mock
    ProductRepository productRepository;
    @Mock
    CategoryRepository categoryRepository;
    @Mock
    ProductMapper productMapper;
    @Mock
    ProductDataValidator validator;

    @BeforeEach
    void setup() {
        testServer = new ProductServiceImpl(productRepository, categoryRepository, validator, productMapper);
    }

    @Test
    void createProductShouldCreateNewProduct() {
        ProductDTO productDTO = ProductDTO.builder()
                .name("mouse")
                .description("WiFi mouse USB")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();


        when(validator.isValid(productDTO)).thenReturn(true);

        Category category = Category.builder().name("PC").build();
        when(categoryRepository.findByName("PC")).thenReturn(Optional.of(category));

        Product product = Product.builder().build();
        when(productMapper.mapDTOToProduct(productDTO)).thenReturn(product);

        testServer.createProduct(productDTO, "PC");

        ArgumentCaptor<Product> argument = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(argument.capture());

        Product captureProduct = argument.getValue();
        assertThat(captureProduct).isNotNull();
        assertThat(captureProduct).isEqualTo(product);
    }

    @Test
    void createProductShouldThrowExceptionIncorrectProductData() {
        ProductDTO productDTO = ProductDTO.builder().build();

        assertThatThrownBy(() -> testServer.createProduct(productDTO, "PS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The product data is not correct! Try again!");

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void createProductShouldThrowExceptionIncorrectCategoryName() {
        ProductDTO productDTO = ProductDTO.builder()
                .name("mouse")
                .description("WiFi mouse")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();

        assertThatThrownBy(() -> testServer.createProduct(productDTO, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The product data is not correct! Try again!");

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void createProductShouldThrowExceptionCategoryDoesNotExist() {
        ProductDTO productDTO = ProductDTO.builder()
                .name("mouse")
                .description("WiFi mouse")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();

        when(validator.isValid(productDTO)).thenReturn(true);

        assertThatThrownBy(() -> testServer.createProduct(productDTO, "PC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such category: " + "PC");

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void getProducts() {
        testServer.getProducts();

        verify(productRepository).findAll();
    }

    @Test
    void getProductsByCategoryShouldReturnProductsIfCategoryExist() {
        Category category = Category.builder().name("PC").build();

        when(categoryRepository.findByName(any())).thenReturn(Optional.of(category));

        testServer.getProductsByCategory("PC");

        verify(productRepository).findAllByCategoryOrderByNameAsc(category);
    }

    @Test
    void getProductsByCategoryShouldThrowExceptionIfCategoryNotExist() {
        assertThatThrownBy(() -> testServer.getProductsByCategory(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such category: " + "");

        verify(productRepository, never()).findAllByCategoryOrderByNameAsc(any());
    }

    @Test
    void updateProduct() {
        ProductDTO productDto = ProductDTO.builder()
                .name("aaaaa")
                .description("aaaaaaaaaaa")
                .price(BigDecimal.valueOf(0.01))
                .build();
        when(validator.isValid(productDto)).thenReturn(true);
        testServer.updateProduct("aaaaa", productDto);
        verify(productRepository).update("aaaaa", "aaaaaaaaaaa", BigDecimal.valueOf(0.01), null, false);
    }

    @Test
    void deleteProductShouldDeleteProductByName() {
        productRepository.saveAndFlush(Product.builder().name("aaaaa").build());

        testServer.deleteProduct("aaaaa");
        boolean isExist = productRepository.findByName("aaaaa").isPresent();

        verify(productRepository).deleteByName("aaaaa");
        assertThat(isExist).isFalse();
    }
}