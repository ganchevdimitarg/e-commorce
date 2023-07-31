package com.concordeu.catalog.category;

import com.concordeu.catalog.controller.CategoryController;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

@WebFluxTest(controllers = CategoryController.class)
@ActiveProfiles("test")
@Tag("integration")
class CategoryControllerTest {
    /*
    @Autowired
    WebTestClient client;
    @MockBean
    CategoryService categoryService;
    @MockBean
    MapStructMapper mapper;

    @Test
    void createCategoryShouldCreateCategory() throws Exception {
        CategoryDTO requestDto = new CategoryDTO("pc");
        when(mapper.mapCategoryRequestDtoToCategoryDto(requestDto)).thenReturn(new CategoryDTO("", "pc", new ArrayList<>()));
        this.client.mutateWith(csrf())
                .mutateWith(mockUser("admin"))
                .post()
                .uri("/api/v1/catalog/category/create-category")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue("""
                            {
                                "name": "pc"
                            }
                        """)
                .exchange()
                .expectStatus().isOk();

        verify(categoryService).createCategory(any(CategoryDTO.class));
    }

    @Test
    void deleteCategoryShouldDeleteProduct() throws Exception {
        CategoryDTO requestDto = new CategoryDTO("pc");
        when(mapper.mapCategoryRequestDtoToCategoryDto(requestDto)).thenReturn(new CategoryDTO("", "pc", new ArrayList<>()));
        this.client.mutateWith(csrf())
                .mutateWith(mockUser("admin"))
                .delete()
                .uri("/api/v1/catalog/category/delete-category?categoryName={categoryName}", "category1")
                .exchange()
                .expectStatus().isOk();

        verify(categoryService).deleteCategory(any());
    }

    @Test
    void moveAllProductsShouldMoveOneProductFromOneCategoryToAnotherCategory() throws Exception {
        this.client.mutateWith(csrf())
                .mutateWith(mockUser("admin"))
                .post()
                .uri("/api/v1/catalog/category/move-one-product?categoryNameFrom=pc&categoryNameTo=acc&productName=mouse")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk();

        verify(categoryService).moveOneProduct(any(), any(), any());
    }

    @Test
    void moveOneProductShouldMoveAllProductsFromOneCategoryToAnotherCategory() throws Exception {
        this.client.mutateWith(csrf())
                .mutateWith(mockUser("admin"))
                .post()
                .uri("/api/v1/catalog/category/move-all-products?categoryNameFrom=pc&categoryNameTo=acc")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk();

        verify(categoryService).moveAllProducts(any(), any());
    }

     */
}