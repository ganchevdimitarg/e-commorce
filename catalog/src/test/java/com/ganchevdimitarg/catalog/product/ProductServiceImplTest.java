package com.ganchevdimitarg.catalog.product;


import com.ganchevdimitarg.catalog.cache.ProductCache;
import com.ganchevdimitarg.catalog.repository.CategoryRepository;
import com.ganchevdimitarg.catalog.repository.ProductRepository;
import com.ganchevdimitarg.catalog.domain.Category;
import com.ganchevdimitarg.catalog.domain.Product;
import com.ganchevdimitarg.catalog.dto.product.CreateProductCommand;
import com.ganchevdimitarg.catalog.dto.product.ProductResponseDto;
import com.ganchevdimitarg.catalog.dto.product.ProductSearchCriteria;
import com.ganchevdimitarg.catalog.dto.product.UpdateProductCommand;
import com.ganchevdimitarg.catalog.event.OutboxEventWriter;
import com.ganchevdimitarg.catalog.exception.ConflictException;
import com.ganchevdimitarg.catalog.exception.NotFoundException;
import com.ganchevdimitarg.catalog.exception.PreconditionFailedException;
import com.ganchevdimitarg.catalog.mapper.MapStructMapper;
import com.ganchevdimitarg.catalog.service.product.ProductService;
import com.ganchevdimitarg.catalog.service.product.ProductServiceImpl;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

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
    private static final AuditorAware<String> AUDITOR = () -> Optional.of("alice");

    private ProductService testService;
    @Mock
    ProductRepository productRepository;
    @Mock
    CategoryRepository categoryRepository;
    @Mock
    MapStructMapper mapStructMapper;
    @Mock
    OutboxEventWriter outboxEventWriter;
    @Mock
    ProductCache productCache;

    @BeforeEach
    void setup() {
        testService = new ProductServiceImpl(productRepository, categoryRepository, mapStructMapper, new SimpleMeterRegistry(), outboxEventWriter, productCache, AUDITOR);
    }

    @Test
    void should_createNewProduct_when_categoryExistsAndNameUnique() {
        CreateProductCommand cmd = new CreateProductCommand("mouse", "WiFi mouse USB", BigDecimal.ONE, true, "", "PC");
        Category category = new Category();
        category.setName("PC");
        when(categoryRepository.findByName("PC")).thenReturn(Optional.of(category));
        Product product = new Product();
        product.setId(P1);
        product.setName("mouse");
        product.setPrice(BigDecimal.ONE);
        product.setInStock(true);
        product.setCategory(category);
        when(mapStructMapper.mapCreateCommandToProduct(cmd)).thenReturn(product);
        ProductResponseDto expectedDto = new ProductResponseDto(P1, "mouse", "WiFi mouse USB",
                BigDecimal.ONE, true, "", null, List.of(), 0L);
        when(mapStructMapper.mapProductToProductResponseDto(product)).thenReturn(expectedDto);

        ProductResponseDto result = testService.createProduct(cmd);

        ArgumentCaptor<Product> argument = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(argument.capture());
        assertThat(argument.getValue().isInStock()).isTrue();
        verify(outboxEventWriter).writeCreated(product);
        verify(productCache).put(P1, expectedDto);
        assertThat(result).isEqualTo(expectedDto);
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
    void should_searchAllProducts_when_categoryNameIsAbsent() {
        PageRequest pageRequest = PageRequest.of(0, 2);
        Product p = new Product();
        p.setId(P1);
        p.setName("mouse");
        p.setCategory(new Category());
        Page<Product> page = new PageImpl<>(List.of(p), pageRequest, 1);
        when(productRepository.findAll(ArgumentMatchers.<Specification<Product>>any(), eq(pageRequest)))
                .thenReturn(page);

        Page<ProductResponseDto> result = testService.searchProducts(
                new ProductSearchCriteria(null, null, null, null, null), pageRequest);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("mouse");
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(categoryRepository, never()).findByName(any());
    }

    @Test
    void should_resolveCategoryId_when_categoryNameIsPresent() {
        Category category = new Category();
        category.setId(CAT_1);
        when(categoryRepository.findByName("pc")).thenReturn(Optional.of(category));

        Pageable pageRequest = PageRequest.of(1, 5);
        List<Product> products = Arrays.asList(new Product(), new Product());
        Page<Product> page = new PageImpl<>(products, pageRequest, products.size());

        when(productRepository.findAll(ArgumentMatchers.<Specification<Product>>any(), eq(pageRequest)))
                .thenReturn(page);

        testService.searchProducts(
                new ProductSearchCriteria(null, null, null, null, "pc"), pageRequest);

        verify(categoryRepository).findByName("pc");
        verify(productRepository)
                .findAll(ArgumentMatchers.<Specification<Product>>any(), eq(pageRequest));
    }

    @Test
    void should_throwNotFound_when_categoryNameIsUnknown() {
        when(categoryRepository.findByName("missing")).thenReturn(Optional.empty());

        Pageable pageRequest = PageRequest.of(0, 20);

        assertThatThrownBy(() -> testService.searchProducts(
                new ProductSearchCriteria(null, null, null, null, "missing"), pageRequest))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Category not found: missing");

        verify(productRepository, never())
                .findAll(ArgumentMatchers.<Specification<Product>>any(), any(Pageable.class));
    }

    @Test
    void should_updateProductById_when_productExists() {
        Product existing = new Product();
        existing.setId(P1);
        existing.setVersion(1L);
        Product refreshed = new Product();
        refreshed.setId(P1);
        refreshed.setName("mouse");
        refreshed.setDescription("aaaaaaaaaaa");
        refreshed.setPrice(BigDecimal.ONE);
        refreshed.setCharacteristics("");
        refreshed.setInStock(false);
        refreshed.setCategory(productCategory());
        when(productRepository.findById(P1))
                .thenReturn(Optional.of(existing), Optional.of(refreshed));
        when(productRepository.updateById(P1, "aaaaaaaaaaa", BigDecimal.ONE, "", false, 1L, "alice")).thenReturn(1);
        ProductResponseDto expectedDto = new ProductResponseDto(P1, "mouse", "aaaaaaaaaaa",
                BigDecimal.ONE, false, "", null, List.of(), 2L);
        when(mapStructMapper.mapProductToProductResponseDto(refreshed)).thenReturn(expectedDto);
        UpdateProductCommand cmd = new UpdateProductCommand("aaaaaaaaaaa", BigDecimal.ONE, false, "");

        ProductResponseDto result = testService.updateProduct(P1, cmd, null);

        verify(productRepository).updateById(P1, "aaaaaaaaaaa", BigDecimal.ONE, "", false, 1L, "alice");
        verify(outboxEventWriter).writeUpdated(refreshed);
        verify(productCache).put(P1, expectedDto);
        assertThat(result).isEqualTo(expectedDto);
    }

    @Test
    void should_update_when_expectedVersionMatches() {
        Product existing = new Product();
        existing.setId(P1);
        existing.setVersion(1L);
        Product refreshed = new Product();
        refreshed.setId(P1);
        refreshed.setName("mouse");
        refreshed.setDescription("aaaaaaaaaaa");
        refreshed.setPrice(BigDecimal.ONE);
        refreshed.setCharacteristics("");
        refreshed.setInStock(false);
        refreshed.setCategory(productCategory());
        refreshed.setVersion(2L);
        when(productRepository.findById(P1))
                .thenReturn(Optional.of(existing), Optional.of(refreshed));
        when(productRepository.updateById(P1, "aaaaaaaaaaa", BigDecimal.ONE, "", false, 1L, "alice")).thenReturn(1);
        ProductResponseDto expectedDto = new ProductResponseDto(P1, "mouse", "aaaaaaaaaaa",
                BigDecimal.ONE, false, "", null, List.of(), 2L);
        when(mapStructMapper.mapProductToProductResponseDto(refreshed)).thenReturn(expectedDto);
        UpdateProductCommand cmd = new UpdateProductCommand("aaaaaaaaaaa", BigDecimal.ONE, false, "");

        ProductResponseDto result = testService.updateProduct(P1, cmd, 1L);

        verify(productRepository).updateById(P1, "aaaaaaaaaaa", BigDecimal.ONE, "", false, 1L, "alice");
        assertThat(result).isEqualTo(expectedDto);
    }

    @Test
    void should_throwPreconditionFailed_when_expectedVersionDiffers() {
        Product existing = new Product();
        existing.setId(P1);
        existing.setVersion(1L);
        when(productRepository.findById(P1)).thenReturn(Optional.of(existing));
        UpdateProductCommand cmd = new UpdateProductCommand("aaaaaaaaaaa", BigDecimal.ONE, false, "");

        assertThatThrownBy(() -> testService.updateProduct(P1, cmd, 2L))
                .isInstanceOf(PreconditionFailedException.class)
                .hasMessageContaining("Product was modified; refresh and retry: " + P1);

        verify(productRepository, never())
                .updateById(any(), any(), any(), any(), anyBoolean(), anyLong(), anyString());
        verify(productCache, never()).put(any(), any());
    }

    @Test
    void should_throwOptimisticLock_when_updateByIdVersionMismatch() {
        Product existing = new Product();
        existing.setId(P1);
        existing.setVersion(1L);
        when(productRepository.findById(P1)).thenReturn(Optional.of(existing));
        when(productRepository.updateById(P1, "aaaaaaaaaaa", BigDecimal.ONE, "", false, 1L, "alice")).thenReturn(0);
        UpdateProductCommand cmd = new UpdateProductCommand("aaaaaaaaaaa", BigDecimal.ONE, false, "");

        assertThatThrownBy(() -> testService.updateProduct(P1, cmd, null))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void should_notPutCache_when_updateFails() {
        Product existing = new Product();
        existing.setId(P1);
        existing.setVersion(1L);
        when(productRepository.findById(P1)).thenReturn(Optional.of(existing));
        when(productRepository.updateById(P1, "aaaaaaaaaaa", BigDecimal.ONE, "", false, 1L, "alice")).thenReturn(0);
        UpdateProductCommand cmd = new UpdateProductCommand("aaaaaaaaaaa", BigDecimal.ONE, false, "");

        assertThatThrownBy(() -> testService.updateProduct(P1, cmd, null))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(productCache, never()).put(any(), any());
    }

    @Test
    void should_throwNotFound_when_productDisappearsBeforeCachePut() {
        Product existing = new Product();
        existing.setId(P1);
        existing.setVersion(1L);
        when(productRepository.findById(P1))
                .thenReturn(Optional.of(existing), Optional.empty());
        when(productRepository.updateById(P1, "aaaaaaaaaaa", BigDecimal.ONE, "", false, 1L, "alice")).thenReturn(1);
        UpdateProductCommand cmd = new UpdateProductCommand("aaaaaaaaaaa", BigDecimal.ONE, false, "");

        assertThatThrownBy(() -> testService.updateProduct(P1, cmd, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found: " + P1);

        verify(productCache, never()).put(any(), any());
    }

    @Test
    void should_throwNotFound_when_updateMissingProduct() {
        UpdateProductCommand cmd = new UpdateProductCommand("aaaaaaaaaaa", BigDecimal.ONE, false, "");
        assertThatThrownBy(() -> testService.updateProduct(MISSING, cmd, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Product not found: " + MISSING);
    }

    @Test
    void should_deleteProductById_when_productExists() {
        Product existing = new Product();
        existing.setId(P1);
        existing.setName("mouse");
        existing.setPrice(BigDecimal.ONE);
        existing.setInStock(true);
        existing.setCategory(productCategory());
        when(productRepository.findById(P1)).thenReturn(Optional.of(existing));

        testService.deleteProduct(P1);

        verify(productRepository).delete(existing);
        verify(productCache).evict(P1);
        verify(outboxEventWriter).writeDeleted(existing);
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
        ProductResponseDto expectedDto = new ProductResponseDto(P1, "mouse", "", BigDecimal.ZERO, true, "", null, new ArrayList<>(), 0L);
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
        ProductResponseDto expectedDto = new ProductResponseDto(PROD_123, "mouse", "", BigDecimal.ZERO, true, "", null, new ArrayList<>(), 0L);
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
        Product product1 = product(ID_1, "p1");
        Product product2 = product(ID_2, "p2");
        when(productRepository.findAllById(List.of(ID_1, ID_2)))
                .thenReturn(List.of(product1, product2));

        List<ProductResponseDto> result = testService.getProductsById(items);

        assertThat(result).containsExactly(productDto(product1), productDto(product2));
    }

    @Test
    void should_returnProductsInRequestOrder_when_repositoryReturnsUnordered() {
        ItemRequestDto items = new ItemRequestDto(List.of(ID_1, ID_2));
        Product product1 = product(ID_1, "p1");
        Product product2 = product(ID_2, "p2");
        when(productRepository.findAllById(List.of(ID_1, ID_2)))
                .thenReturn(List.of(product2, product1));

        List<ProductResponseDto> result = testService.getProductsById(items);

        assertThat(result).containsExactly(productDto(product1), productDto(product2));
    }

    @Test
    void should_throwNotFoundListingEveryMissingId_when_anyIdMissing() {
        ItemRequestDto items = new ItemRequestDto(List.of(ID_1, MISSING, NONEXISTENT));
        when(productRepository.findAllById(List.of(ID_1, MISSING, NONEXISTENT)))
                .thenReturn(List.of(product(ID_1, "p1")));

        assertThatThrownBy(() -> testService.getProductsById(items))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Products not found: [" + MISSING + ", " + NONEXISTENT + "]");
    }

    @Test
    void should_throwNotFound_when_getProductsByIdWithUnknownId() {
        ItemRequestDto items = new ItemRequestDto(List.of(MISSING));
        when(productRepository.findAllById(List.of(MISSING))).thenReturn(List.of());

        assertThatThrownBy(() -> testService.getProductsById(items))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Products not found: [" + MISSING + "]");
    }

    private static Product product(UUID id, String name) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setDescription("");
        product.setPrice(BigDecimal.ZERO);
        product.setInStock(true);
        product.setCharacteristics("");
        return product;
    }

    private static Category productCategory() {
        Category category = new Category();
        category.setName("peripherals");
        return category;
    }

    private static ProductResponseDto productDto(Product product) {
        return new ProductResponseDto(
                product.getId(), product.getName(), product.getDescription(), product.getPrice(),
                product.isInStock(), product.getCharacteristics(), null, List.of(), product.getVersion());
    }

    @Test
    void should_incrementCreatedCounter_when_productCreated() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProductServiceImpl service =
                new ProductServiceImpl(productRepository, categoryRepository, mapStructMapper, registry, outboxEventWriter, productCache, AUDITOR);

        CreateProductCommand cmd = new CreateProductCommand("mouse", "WiFi mouse USB", BigDecimal.ONE, true, "", "PC");
        Category category = new Category();
        category.setName("PC");
        when(categoryRepository.findByName("PC")).thenReturn(Optional.of(category));
        Product product = new Product();
        product.setId(P1);
        when(mapStructMapper.mapCreateCommandToProduct(cmd)).thenReturn(product);
        when(mapStructMapper.mapProductToProductResponseDto(product)).thenReturn(
                new ProductResponseDto(P1, "mouse", "WiFi mouse USB", BigDecimal.ONE, true, "", null, List.of(), 0L));

        service.createProduct(cmd);

        assertThat(registry.get("catalog.product.created").counter().count()).isEqualTo(1.0);
    }
}
