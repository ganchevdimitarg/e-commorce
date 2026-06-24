package com.concordeu.catalog.product;


import com.concordeu.catalog.repository.CategoryRepository;
import com.concordeu.catalog.repository.ProductRepository;
import com.concordeu.catalog.domain.Category;
import com.concordeu.catalog.domain.Product;
import com.concordeu.catalog.dto.product.CreateProductCommand;
import com.concordeu.catalog.dto.product.ProductResponseDto;
import com.concordeu.catalog.dto.product.UpdateProductCommand;
import com.concordeu.catalog.event.ProductEventPublisher;
import com.concordeu.catalog.exception.ConflictException;
import com.concordeu.catalog.exception.NotFoundException;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.service.product.ProductService;
import com.concordeu.catalog.service.product.ProductServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

import com.concordeu.catalog.dto.product.ItemRequestDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

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
    ProductEventPublisher productEventPublisher;

    @BeforeEach
    void setup() {
        testService = new ProductServiceImpl(productRepository, categoryRepository, mapStructMapper, new SimpleMeterRegistry(), productEventPublisher);
    }

    @Test
    void should_createNewProduct_when_categoryExistsAndNameUnique() {
        CreateProductCommand cmd = new CreateProductCommand("mouse", "WiFi mouse USB", BigDecimal.ONE, true, "", "PC");
        Category category = new Category();
        category.setName("PC");
        when(categoryRepository.findByName("PC")).thenReturn(Optional.of(category));
        Product product = new Product();
        product.setInStock(true);
        when(mapStructMapper.mapCreateCommandToProduct(cmd)).thenReturn(product);

        testService.createProduct(cmd);

        ArgumentCaptor<Product> argument = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(argument.capture());
        assertThat(argument.getValue().isInStock()).isTrue();
        verify(productEventPublisher).publishCreated(product.getId(), product.getName());
    }

    @Test
    void should_throwConflict_when_createProductWithExistingName() {
        CreateProductCommand cmd = new CreateProductCommand("mouse", "WiFi mouse USB", BigDecimal.ONE, true, "", "PC");
        Product existingProduct = new Product();
        existingProduct.setName("mouse");
        when(productRepository.findByName("mouse")).thenReturn(Optional.of(existingProduct));

        Category categoryForCreate = new Category();
        categoryForCreate.setName("PC");
        when(categoryRepository.findByName("PC")).thenReturn(Optional.of(categoryForCreate));

        assertThatThrownBy(() -> testService.createProduct(cmd))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Product with the name: mouse already exist.");

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void should_throwNotFound_when_createProductCategoryDoesNotExist() {
        CreateProductCommand cmd = new CreateProductCommand("mouse", "WiFi mouse USB", BigDecimal.ONE, true, "", "PC");

        assertThatThrownBy(() -> testService.createProduct(cmd))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Category not found: PC");

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void should_returnMappedProductsPage_when_pageRequested() {
        PageRequest pageRequest = PageRequest.of(0, 2);
        Product p = new Product();
        p.setId("p1");
        p.setName("mouse");
        p.setCategory(new Category());
        Page<Product> page = new PageImpl<>(List.of(p), pageRequest, 1);
        when(productRepository.findAll(pageRequest)).thenReturn(page);

        Page<ProductResponseDto> result = testService.getProductsByPage(pageRequest);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("mouse");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void should_returnProducts_when_categoryExists() {
        Category category = new Category();
        category.setId("1");
        when(categoryRepository.findByName("pc")).thenReturn(Optional.of(category));

        Pageable pageRequest = PageRequest.of(1, 5);
        List<Product> products = Arrays.asList(new Product(), new Product());
        Page<Product> page = new PageImpl<>(products, pageRequest, products.size());

        when(productRepository.findAllByCategoryIdByPage("1", pageRequest)).thenReturn(page);

        testService.getProductsByCategoryByPage(pageRequest, "pc");

        verify(productRepository).findAllByCategoryIdByPage(category.getId(), pageRequest);
    }

    @Test
    void should_throwNotFound_when_getProductsByMissingCategory() {
        Pageable pageRequest = PageRequest.of(1, 5);
        assertThatThrownBy(() -> testService.getProductsByCategoryByPage(pageRequest, ""))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Category not found: ");

        verify(productRepository, never()).findAllByCategoryIdByPage(new Category().getId(), pageRequest);
    }

    @Test
    void should_updateProductById_when_productExists() {
        Product existing = new Product();
        existing.setId("p1");
        existing.setVersion(1L);
        when(productRepository.findById("p1")).thenReturn(Optional.of(existing));
        when(productRepository.updateById("p1", "aaaaaaaaaaa", BigDecimal.ONE, "", false, 1L)).thenReturn(1);
        UpdateProductCommand cmd = new UpdateProductCommand("aaaaaaaaaaa", BigDecimal.ONE, false, "");

        testService.updateProduct("p1", cmd);

        verify(productRepository).updateById("p1", "aaaaaaaaaaa", BigDecimal.ONE, "", false, 1L);
        verify(productEventPublisher).publishUpdated("p1", existing.getName());
    }

    @Test
    void should_throwOptimisticLock_when_updateByIdVersionMismatch() {
        Product existing = new Product();
        existing.setId("p1");
        existing.setVersion(1L);
        when(productRepository.findById("p1")).thenReturn(Optional.of(existing));
        when(productRepository.updateById("p1", "aaaaaaaaaaa", BigDecimal.ONE, "", false, 1L)).thenReturn(0);
        UpdateProductCommand cmd = new UpdateProductCommand("aaaaaaaaaaa", BigDecimal.ONE, false, "");

        assertThatThrownBy(() -> testService.updateProduct("p1", cmd))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void should_throwNotFound_when_updateMissingProduct() {
        UpdateProductCommand cmd = new UpdateProductCommand("aaaaaaaaaaa", BigDecimal.ONE, false, "");
        assertThatThrownBy(() -> testService.updateProduct("bbbbb", cmd))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found: bbbbb");
    }

    @Test
    void should_deleteProductById_when_productExists() {
        Product existing = new Product();
        existing.setId("p1");
        existing.setName("mouse");
        when(productRepository.findById("p1")).thenReturn(Optional.of(existing));

        testService.deleteProduct("p1");

        verify(productRepository).delete(existing);
        verify(productEventPublisher).publishDeleted("p1", "mouse");
    }

    @Test
    void should_throwNotFound_when_deleteMissingProduct() {
        assertThatThrownBy(() -> testService.deleteProduct("bbbbb"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found: bbbbb");

        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    void should_returnProduct_when_getProductByNameWithValidName() {
        Product product = new Product();
        product.setName("mouse");
        when(productRepository.findByName("mouse")).thenReturn(Optional.of(product));
        ProductResponseDto expectedDto = new ProductResponseDto("", "mouse", "", BigDecimal.ZERO, true, "", null, new ArrayList<>());
        when(mapStructMapper.mapProductToProductResponseDto(product)).thenReturn(expectedDto);

        ProductResponseDto result = testService.getProductByName("mouse");

        assertThat(result).isNotNull();
        assertThat(result.name()).isEqualTo("mouse");
    }

    // Validation of null/blank name now enforced at the controller layer (@NotBlank)

    @Test
    void should_throwNotFound_when_getProductByNameNotFound() {
        when(productRepository.findByName("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testService.getProductByName("nonexistent"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found: nonexistent");
    }

    @Test
    void should_returnProduct_when_getProductByIdWithValidId() {
        Product product = new Product();
        product.setId("123");
        when(productRepository.findById("123")).thenReturn(Optional.of(product));
        ProductResponseDto expectedDto = new ProductResponseDto("123", "mouse", "", BigDecimal.ZERO, true, "", null, new ArrayList<>());
        when(mapStructMapper.mapProductToProductResponseDto(product)).thenReturn(expectedDto);

        ProductResponseDto result = testService.getProductById("123");

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo("123");
    }

    // Validation of null/blank id now enforced at the controller layer (@NotBlank)

    @Test
    void should_throwNotFound_when_getProductByIdNotFound() {
        when(productRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testService.getProductById("nonexistent"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found: nonexistent");
    }

    @Test
    void should_returnProducts_when_getProductsByIdWithValidItems() {
        ItemRequestDto items = new ItemRequestDto(List.of("id1", "id2"));

        Product product1 = new Product();
        product1.setId("id1");
        Product product2 = new Product();
        product2.setId("id2");

        when(productRepository.findById("id1")).thenReturn(Optional.of(product1));
        when(productRepository.findById("id2")).thenReturn(Optional.of(product2));

        ProductResponseDto dto1 = new ProductResponseDto("id1", "p1", "", BigDecimal.ZERO, true, "", null, new ArrayList<>());
        ProductResponseDto dto2 = new ProductResponseDto("id2", "p2", "", BigDecimal.ZERO, true, "", null, new ArrayList<>());
        when(mapStructMapper.mapProductToProductResponseDto(product1)).thenReturn(dto1);
        when(mapStructMapper.mapProductToProductResponseDto(product2)).thenReturn(dto2);

        List<ProductResponseDto> result = testService.getProductsById(items);

        assertThat(result).containsExactly(dto1, dto2);
    }

    @Test
    void should_throwNotFound_when_getProductsByIdWithUnknownId() {
        ItemRequestDto items = new ItemRequestDto(List.of("missing"));
        when(productRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testService.getProductsById(items))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found: missing");
    }

    @Test
    void should_incrementCreatedCounter_when_productCreated() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProductServiceImpl service =
                new ProductServiceImpl(productRepository, categoryRepository, mapStructMapper, registry, productEventPublisher);

        CreateProductCommand cmd = new CreateProductCommand("mouse", "WiFi mouse USB", BigDecimal.ONE, true, "", "PC");
        Category category = new Category();
        category.setName("PC");
        when(categoryRepository.findByName("PC")).thenReturn(Optional.of(category));
        when(mapStructMapper.mapCreateCommandToProduct(cmd)).thenReturn(new Product());

        service.createProduct(cmd);

        assertThat(registry.get("catalog.product.created").counter().count()).isEqualTo(1.0);
    }
}
