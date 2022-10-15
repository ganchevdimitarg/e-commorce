package com.concordeu.catalog.product;


import com.concordeu.catalog.MapStructMapper;
import com.concordeu.catalog.dao.ProductRepository;
import com.concordeu.catalog.domain.Category;
import com.concordeu.catalog.dao.CategoryRepository;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dto.ProductDto;
import com.concordeu.catalog.service.product.ProductService;
import com.concordeu.catalog.service.product.ProductServiceImpl;
import com.concordeu.catalog.validator.ProductDataValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    private ProductService testService;
    @Mock
    ProductRepository productRepository;
    @Mock
    CategoryRepository categoryRepository;
    @Mock
    MapStructMapper mapStructMapper;
    @Mock
    ProductDataValidator validator;

    @BeforeEach
    void setup() {
        testService = new ProductServiceImpl(productRepository, categoryRepository, validator, mapStructMapper);
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
        when(mapStructMapper.mapDtoToProduct(productDto)).thenReturn(product);

        testService.createProduct(productDto, "PC");

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

        assertThatThrownBy(() -> testService.createProduct(productDto, categoryName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product with the name: " + productDto.getName() + " already exist.");

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

        assertThatThrownBy(() -> testService.createProduct(productDto, categoryName))
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

        assertThatThrownBy(() -> testService.createProduct(productDto, categoryName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such category: " + categoryName);

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void getProductsPage() {
        PageRequest pageRequest = PageRequest.of(1, 5);
        List<Product> products = Arrays.asList(new Product(), new Product());
        Page<Product> page = new PageImpl<>(products, pageRequest, products.size());
        when(productRepository.findAll(pageRequest)).thenReturn(page);

        testService.getProductsByPage(1, 5);

        verify(productRepository).findAll(pageRequest);
    }

    @Test
    @Disabled
    void getProductsByCategoryByPageByCategoryShouldReturnProductsIfCategoryExist() {
        PageRequest pageRequest = PageRequest.of(1, 5);
        List<Product> products = Arrays.asList(new Product(), new Product());
        Page<Product> page = new PageImpl<>(products, pageRequest, products.size());
        when(productRepository.findAll(pageRequest)).thenReturn(page);

        Category category = Category.builder().name("PC").build();
        when(categoryRepository.findByName(any())).thenReturn(Optional.of(category));

        testService.getProductsByCategoryByPage(1,5,"pc");

        verify(productRepository).findAllByCategoryIdByPage(category.getId(), pageRequest);
    }

    @Test
    void getProductsByCategoryShouldThrowExceptionIfCategoryNotExist() {
        PageRequest pageRequest = PageRequest.of(1, 5);

        assertThatThrownBy(() -> testService.getProductsByCategoryByPage(1,2,""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such category: " + "");

        verify(productRepository, never()).findAllByCategoryIdByPage(new Category().getId(), pageRequest);
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
        testService.updateProduct(productDto, productName);
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
        assertThatThrownBy(() -> testService.updateProduct(productDto, "bbbbb"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product with the name: bbbbb does not exist.");
    }

    @Test
    void deleteProductShouldDeleteProductIfProductExist() {
        String productName = "aaaaa";
        when(productRepository.findByName(productName)).thenReturn(Optional.of(Product.builder().name(productName).build()));

        testService.deleteProduct(productName);

        verify(productRepository).deleteByName(productName);
    }

    @Test
    void deleteProductShouldDeleteIfProductDoesNotExist() {
        assertThatThrownBy(() -> testService.deleteProduct("bbbbb"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product with the name: bbbbb does not exist.");

        verify(productRepository, never()).deleteByName(any());
    }
}