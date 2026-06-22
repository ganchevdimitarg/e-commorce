package com.concordeu.catalog.product;


import com.concordeu.catalog.dao.CategoryDao;
import com.concordeu.catalog.dao.ProductDao;
import com.concordeu.catalog.domain.Category;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dto.product.ProductResponseDto;
import com.concordeu.catalog.excaption.ConflictException;
import com.concordeu.catalog.excaption.NotFoundException;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.service.product.ProductService;
import com.concordeu.catalog.service.product.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    private ProductService testService;
    @Mock
    ProductDao productDao;
    @Mock
    CategoryDao categoryDao;
    @Mock
    MapStructMapper mapStructMapper;

    ProductResponseDto productResponseDto;

    @BeforeEach
    void setup() {
        testService = new ProductServiceImpl(productDao, categoryDao, mapStructMapper);
        productResponseDto = new ProductResponseDto("","mouse", "WiFi mouse USB",
                BigDecimal.ONE, true, "", null, new ArrayList<>());
    }

    @Test
    void createProductShouldCreateNewProduct() {
        String categoryName = "PC";

        Category category = new Category();
        category.setName(categoryName);
        when(categoryDao.findByName(categoryName)).thenReturn(Optional.of(category));

        Product product = new Product();
        when(mapStructMapper.mapProductResponseDtoToProduct(productResponseDto)).thenReturn(product);

        testService.createProduct(productResponseDto, "PC");

        ArgumentCaptor<Product> argument = ArgumentCaptor.forClass(Product.class);
        verify(productDao).saveAndFlush(argument.capture());

        Product captureProduct = argument.getValue();
        assertThat(captureProduct).isNotNull();
        assertThat(captureProduct).isEqualTo(product);
    }

    @Test
    void should_throwConflict_when_createProductWithExistingName() {
        Product existingProduct = new Product();
        existingProduct.setName("mouse");
        when(productDao.findByName("mouse")).thenReturn(Optional.of(existingProduct));

        String categoryName = "PC";
        Category categoryForCreate = new Category();
        categoryForCreate.setName(categoryName);
        when(categoryDao.findByName(categoryName)).thenReturn(Optional.of(categoryForCreate));

        assertThatThrownBy(() -> testService.createProduct(productResponseDto, categoryName))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Product with the name: " + productResponseDto.name() + " already exist.");

        verify(productDao, never()).saveAndFlush(any());
    }

    @Test
    void should_throwNotFound_when_createProductWithMissingCategory() {
        String categoryName = "";

        assertThatThrownBy(() -> testService.createProduct(productResponseDto, categoryName))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Category not found: ");

        verify(productDao, never()).saveAndFlush(any());
    }

    @Test
    void should_throwNotFound_when_createProductCategoryDoesNotExist() {
        String categoryName = "PC";

        assertThatThrownBy(() -> testService.createProduct(productResponseDto, categoryName))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Category not found: ");

        verify(productDao, never()).saveAndFlush(any());
    }

    @Test
    void getProductsPage() {
        PageRequest pageRequest = PageRequest.of(1, 5);
        List<Product> products = Arrays.asList(new Product(), new Product());
        Page<Product> page = new PageImpl<>(products, pageRequest, products.size());
        when(productDao.findAll(pageRequest)).thenReturn(page);

        testService.getProductsByPage(1, 5);

        verify(productDao).findAll(pageRequest);
    }

    @Test
    void getProductsByCategoryByPageByCategoryShouldReturnProductsIfCategoryExist() {
        Category category = new Category();
        category.setId("1");
        when(categoryDao.findByName("pc")).thenReturn(Optional.of(category));

        Pageable pageRequest = PageRequest.of(1, 5);
        List<Product> products = Arrays.asList(new Product(), new Product());
        Page<Product> page = new PageImpl<>(products, pageRequest, products.size());

        when(productDao.findAllByCategoryIdByPage("1", pageRequest)).thenReturn(page);

        testService.getProductsByCategoryByPage(1, 5, "pc");

        verify(productDao).findAllByCategoryIdByPage(category.getId(), pageRequest);
    }

    @Test
    void should_throwNotFound_when_getProductsByMissingCategory() {
        Pageable pageRequest = PageRequest.of(1, 5);
        assertThatThrownBy(() -> testService.getProductsByCategoryByPage(1, 2, ""))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Category not found: ");

        verify(productDao, never()).findAllByCategoryIdByPage(new Category().getId(), pageRequest);
    }

    @Test
    void updateProductShouldUpdateDataIfProductExist() {
        ProductResponseDto updateProduct = new ProductResponseDto("","mouse", "aaaaaaaaaaa", BigDecimal.ONE, false, "", null, new ArrayList<>());
        Product productToUpdate = new Product();
        productToUpdate.setName(productResponseDto.name());
        when(productDao.findByName(productResponseDto.name())).thenReturn(Optional.of(productToUpdate));
        testService.updateProduct(updateProduct, productResponseDto.name());
        verify(productDao).update(productResponseDto.name(), "aaaaaaaaaaa", BigDecimal.ONE, "", false);
    }

    @Test
    void should_throwNotFound_when_updateMissingProduct() {
        assertThatThrownBy(() -> testService.updateProduct(productResponseDto, "bbbbb"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found: bbbbb");
    }

    @Test
    void deleteProductShouldDeleteProductIfProductExist() {
        String productName = "aaaaa";
        Product productToDelete = new Product();
        productToDelete.setName(productName);
        when(productDao.findByName(productName)).thenReturn(Optional.of(productToDelete));

        testService.deleteProduct(productName);

        verify(productDao).deleteByName(productName);
    }

    @Test
    void should_throwNotFound_when_deleteMissingProduct() {
        assertThatThrownBy(() -> testService.deleteProduct("bbbbb"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found: bbbbb");

        verify(productDao, never()).deleteByName(any());
    }
}
