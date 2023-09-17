package com.concordeu.catalog.product;


import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {
/*
    private ProductService testService;
    @Mock
    ProductRepository productRepository;
    @Mock
    CategoryRepository categoryRepository;
    @Mock
    MapStructMapper mapStructMapper;
    @Mock
    ProductDataValidator validator;

    ProductResponseDto productResponseDto;

    @BeforeEach
    void setup() {
        testService = new ProductServiceImpl(productRepository, categoryRepository, validator, mapStructMapper);
        productResponseDto = new ProductResponseDto("","mouse", "WiFi mouse USB",
                BigDecimal.ONE, true, "", null, new ArrayList<>());
    }

    @Test
    void createProductShouldCreateNewProduct() {
        String categoryName = "PC";

        when(validator.validateData(productResponseDto, categoryName)).thenReturn(true);

        Category category = Category.builder().name(categoryName).build();
        when(categoryRepository.findByName(categoryName)).thenReturn(Optional.of(category));

        Product product = Product.builder().build();
        when(mapStructMapper.mapProductResponseDtoToProduct(productResponseDto)).thenReturn(product);

        testService.createProduct(productResponseDto, "PC");

        ArgumentCaptor<Product> argument = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(argument.capture());

        Product captureProduct = argument.getValue();
        assertThat(captureProduct).isNotNull();
        assertThat(captureProduct).isEqualTo(product);
    }

    @Test
    void createProductShouldThrowExceptionIfProductAlreadyExist() {
        when(productRepository.findByName("mouse")).thenReturn(Optional.of(Product.builder().name("mouse").build()));

        String categoryName = "PC";
        when(categoryRepository.findByName(categoryName)).thenReturn(Optional.of(Category.builder().name(categoryName).build()));

        assertThatThrownBy(() -> testService.createProduct(productResponseDto, categoryName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product with the name: " + productResponseDto.name() + " already exist.");

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void createProductShouldThrowExceptionIncorrectCategoryName() {
        String categoryName = "";

        assertThatThrownBy(() -> testService.createProduct(productResponseDto, categoryName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such category: " + categoryName);

        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void createProductShouldThrowExceptionCategoryDoesNotExist() {
        String categoryName = "PC";

        when(validator.validateData(productResponseDto, categoryName)).thenReturn(true);

        assertThatThrownBy(() -> testService.createProduct(productResponseDto, categoryName))
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
    void getProductsByCategoryByPageByCategoryShouldReturnProductsIfCategoryExist() {
        Category category = Category.builder().id("1").build();
        when(categoryRepository.findByName("pc")).thenReturn(Optional.of(category));

        PageRequest pageRequest = PageRequest.of(1, 5);
        List<Product> products = Arrays.asList(new Product(), new Product());
        Page<Product> page = new PageImpl<>(products, pageRequest, products.size());

        when(productRepository.findAllByCategoryIdByPage("1", pageRequest)).thenReturn(page);

        testService.getProductsByCategoryByPage(1, 5, "pc");

        verify(productRepository).findAllByCategoryIdByPage(category.getId(), pageRequest);
    }

    @Test
    void getProductsByCategoryShouldThrowExceptionIfCategoryNotExist() {
        PageRequest pageRequest = PageRequest.of(1, 5);

        assertThatThrownBy(() -> testService.getProductsByCategoryByPage(1, 2, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No such category: " + "");

        verify(productRepository, never()).findAllByCategoryIdByPage(new Category().getId(), pageRequest);
    }

    @Test
    void updateProductShouldUpdateDataIfProductExist() {
        ProductResponseDto updateProduct = new ProductResponseDto("","mouse", "aaaaaaaaaaa", BigDecimal.ONE, false, "", null, new ArrayList<>());
        when(validator.validateData(updateProduct, productResponseDto.name())).thenReturn(true);
        when(productRepository.findByName(productResponseDto.name())).thenReturn(Optional.of(Product.builder().name(productResponseDto.name()).build()));
        testService.updateProduct(updateProduct, productResponseDto.name());
        verify(productRepository).update(productResponseDto.name(), "aaaaaaaaaaa", BigDecimal.ONE, "", false);
    }

    @Test
    void updateProductShouldThrowExceptionIfProductDoesNotExist() {
        String productName = "mouse";
        when(validator.validateData(productResponseDto, "bbbbb")).thenReturn(true);
        assertThatThrownBy(() -> testService.updateProduct(productResponseDto, "bbbbb"))
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

 */
}
