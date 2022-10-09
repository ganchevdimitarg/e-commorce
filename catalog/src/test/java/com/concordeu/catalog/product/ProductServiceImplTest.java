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
        ProductDto productDto = ProductDto.builder()
                .name("mouse")
                .description("WiFi mouse USB")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();

        String categoryName = "PC";

        when(validator.validateData(productDto, categoryName)).thenReturn(true);

        Category category = Category.builder().name(categoryName).build();
        when(categoryRepository.findByName(categoryName)).thenReturn(Optional.of(category));

        Product product = Product.builder().build();
        when(productMapper.mapDTOToProduct(productDto)).thenReturn(product);

        testServer.createProduct(productDto, "PC");

        ArgumentCaptor<Product> argument = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(argument.capture());

        Product captureProduct = argument.getValue();
        assertThat(captureProduct).isNotNull();
        assertThat(captureProduct).isEqualTo(product);
    }

    @Test
    void createProductShouldThrowExceptionIfProductAlreadyExist() {
        String productName = "aaaaa";
        ProductDto productDto = ProductDto.builder().name(productName).build();
        when(productRepository.findByName(productName)).thenReturn(Optional.of(Product.builder().name(productName).build()));

        String categoryName = "PC";
        when(categoryRepository.findByName(categoryName)).thenReturn(Optional.of(Category.builder().name(categoryName).build()));

        assertThatThrownBy(() -> testServer.createProduct(productDto, categoryName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product with the name: " + productDto.getName() + " already exists.");

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void createProductShouldThrowExceptionIncorrectCategoryName() {
        ProductDto productDto = ProductDto.builder()
                .name("mouse")
                .description("WiFi mouse")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();

        String categoryName = "";

        assertThatThrownBy(() -> testServer.createProduct(productDto, categoryName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such category: " + categoryName);

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void createProductShouldThrowExceptionCategoryDoesNotExist() {
        ProductDto productDto = ProductDto.builder()
                .name("mouse")
                .description("WiFi mouse")
                .price(BigDecimal.valueOf(54.32))
                .inStock(true)
                .build();

        String categoryName = "PC";

        when(validator.validateData(productDto, categoryName)).thenReturn(true);

        assertThatThrownBy(() -> testServer.createProduct(productDto, categoryName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such category: " + categoryName);

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
    void updateProductShouldUpdateDataIfProductExist() {
        String productName = "aaaaa";
        ProductDto productDto = ProductDto.builder()
                .name(productName)
                .description("aaaaaaaaaaa")
                .price(BigDecimal.valueOf(0.01))
                .build();
        when(validator.validateData(productDto, productName)).thenReturn(true);
        when(productRepository.findByName(productName)).thenReturn(Optional.of(Product.builder().name(productName).build()));
        testServer.updateProduct(productName, productDto);
        verify(productRepository).update(productName, "aaaaaaaaaaa", BigDecimal.valueOf(0.01), null, false);
    }

    @Test
    void updateProductShouldThrowExceptionIfProductDoesNotExist() {
        String productName = "aaaaa";
        ProductDto productDto = ProductDto.builder()
                .name(productName)
                .description("aaaaaaaaaaa")
                .price(BigDecimal.valueOf(0.01))
                .build();
        when(validator.validateData(productDto, "bbbbb")).thenReturn(true);
        assertThatThrownBy(() -> testServer.updateProduct("bbbbb", productDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product with the name: bbbbb does not exists.");
    }

    @Test
    void deleteProductShouldDeleteProductIfProductExist() {
        String productName = "aaaaa";
        when(productRepository.findByName(productName)).thenReturn(Optional.of(Product.builder().name(productName).build()));

        testServer.deleteProduct(productName);

        verify(productRepository).deleteByName(productName);
    }

    @Test
    void deleteProductShouldDeleteIfProductDoesNotExist() {
        assertThatThrownBy(() -> testServer.deleteProduct("bbbbb"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product with the name: bbbbb does not exists.");
    }
}