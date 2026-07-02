package com.ganchevdimitarg.catalog.product;


import com.ganchevdimitarg.catalog.repository.CategoryRepository;
import com.ganchevdimitarg.catalog.repository.ProductRepository;
import com.ganchevdimitarg.catalog.domain.Category;
import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.dto.product.CreateProductCommand;
import com.ganchevdimitarg.catalog.dto.product.ProductResponseDto;
import com.ganchevdimitarg.catalog.dto.product.UpdateProductCommand;
import com.ganchevdimitarg.catalog.event.ProductEventPublisher;
import com.ganchevdimitarg.catalog.exception.ConflictException;
import com.ganchevdimitarg.catalog.exception.NotFoundException;
import com.ganchevdimitarg.catalog.mapper.MapStructMapper;
import com.ganchevdimitarg.catalog.service.product.ProductService;
import com.ganchevdimitarg.catalog.service.product.ProductServiceImpl;
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

import com.ganchevdimitarg.catalog.dto.product.ItemRequestDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    private static final UUID P1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CAT_1 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PROD_123 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ID_1 = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID ID_2 = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID MISSING = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID NONEXISTENT = UUID.fromString("77777777-7777-7777-7777-777777777777");

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
        product.setId(P1);
        product.setInStock(true);
        when(mapStructMapper.mapCreateCommandToProduct(cmd)).thenReturn(product);

        testService.createProduct(cmd);

        ArgumentCaptor<Product> argument = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(argument.capture());
        assertThat(argument.getValue().isInStock()).isTrue();
        verify(productEventPublisher).publishCreated(P1.toString(), product.getName());
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
        p.setId(P1);
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
        category.setId(CAT_1);
        when(categoryRepository.findByName("pc")).thenReturn(Optional.of(category));

        Pageable pageRequest = PageRequest.of(1, 5);
        List<Product> products = Arrays.asList(new Product(), new Product());
        Page<Product> page = new PageImpl<>(products, pageRequest, products.size());

        when(productRepository.findAllByCategoryIdByPage(CAT_1, pageRequest)).thenReturn(page);

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
        existing.setId(P1);
        existing.setVersion(1L);
        when(productRepository.findById(P1)).thenReturn(Optional.of(existing));
        when(productRepository.updateById(P1, "aaaaaaaaaaa", BigDecimal.ONE, "", false, 1L)).thenReturn(1);
        UpdateProductCommand cmd = new UpdateProductCommand("aaaaaaaaaaa", BigDecimal.ONE, false, "");

        testService.updateProduct(P1, cmd);

        verify(productRepository).updateById(P1, "aaaaaaaaaaa", BigDecimal.ONE, "", false, 1L);
        verify(productEventPublisher).publishUpdated(P1.toString(), existing.getName());
    }

    @Test
    void should_throwOptimisticLock_when_updateByIdVersionMismatch() {
        Product existing = new Product();
        existing.setId(P1);
        existing.setVersion(1L);
        when(productRepository.findById(P1)).thenReturn(Optional.of(existing));
        when(productRepository.updateById(P1, "aaaaaaaaaaa", BigDecimal.ONE, "", false, 1L)).thenReturn(0);
        UpdateProductCommand cmd = new UpdateProductCommand("aaaaaaaaaaa", BigDecimal.ONE, false, "");

        assertThatThrownBy(() -> testService.updateProduct(P1, cmd))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void should_throwNotFound_when_updateMissingProduct() {
        UpdateProductCommand cmd = new UpdateProductCommand("aaaaaaaaaaa", BigDecimal.ONE, false, "");
        assertThatThrownBy(() -> testService.updateProduct(MISSING, cmd))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found: " + MISSING);
    }

    @Test
    void should_deleteProductById_when_productExists() {
        Product existing = new Product();
        existing.setId(P1);
        existing.setName("mouse");
        when(productRepository.findById(P1)).thenReturn(Optional.of(existing));

        testService.deleteProduct(P1);

        verify(productRepository).delete(existing);
        verify(productEventPublisher).publishDeleted(P1.toString(), "mouse");
    }

    @Test
    void should_throwNotFound_when_deleteMissingProduct() {
        assertThatThrownBy(() -> testService.deleteProduct(MISSING))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found: " + MISSING);

        verify(productRepository, never()).delete(any(Product.class));
    }

    @Test
    void should_returnProduct_when_getProductByNameWithValidName() {
        Product product = new Product();
        product.setName("mouse");
        when(productRepository.findByName("mouse")).thenReturn(Optional.of(product));
        ProductResponseDto expectedDto = new ProductResponseDto(P1, "mouse", "", BigDecimal.ZERO, true, "", null, new ArrayList<>());
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
        product.setId(PROD_123);
        when(productRepository.findById(PROD_123)).thenReturn(Optional.of(product));
        ProductResponseDto expectedDto = new ProductResponseDto(PROD_123, "mouse", "", BigDecimal.ZERO, true, "", null, new ArrayList<>());
        when(mapStructMapper.mapProductToProductResponseDto(product)).thenReturn(expectedDto);

        ProductResponseDto result = testService.getProductById(PROD_123);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(PROD_123);
    }

    // Validation of null/blank id now enforced at the controller layer (path-variable type binding)

    @Test
    void should_throwNotFound_when_getProductByIdNotFound() {
        when(productRepository.findById(NONEXISTENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testService.getProductById(NONEXISTENT))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found: " + NONEXISTENT);
    }

    @Test
    void should_returnProducts_when_getProductsByIdWithValidItems() {
        ItemRequestDto items = new ItemRequestDto(List.of(ID_1, ID_2));

        Product product1 = new Product();
        product1.setId(ID_1);
        Product product2 = new Product();
        product2.setId(ID_2);

        when(productRepository.findById(ID_1)).thenReturn(Optional.of(product1));
        when(productRepository.findById(ID_2)).thenReturn(Optional.of(product2));

        ProductResponseDto dto1 = new ProductResponseDto(ID_1, "p1", "", BigDecimal.ZERO, true, "", null, new ArrayList<>());
        ProductResponseDto dto2 = new ProductResponseDto(ID_2, "p2", "", BigDecimal.ZERO, true, "", null, new ArrayList<>());
        when(mapStructMapper.mapProductToProductResponseDto(product1)).thenReturn(dto1);
        when(mapStructMapper.mapProductToProductResponseDto(product2)).thenReturn(dto2);

        List<ProductResponseDto> result = testService.getProductsById(items);

        assertThat(result).containsExactly(dto1, dto2);
    }

    @Test
    void should_throwNotFound_when_getProductsByIdWithUnknownId() {
        ItemRequestDto items = new ItemRequestDto(List.of(MISSING));
        when(productRepository.findById(MISSING)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> testService.getProductsById(items))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found: " + MISSING);
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
        Product product = new Product();
        product.setId(P1);
        when(mapStructMapper.mapCreateCommandToProduct(cmd)).thenReturn(product);

        service.createProduct(cmd);

        assertThat(registry.get("catalog.product.created").counter().count()).isEqualTo(1.0);
    }
}
