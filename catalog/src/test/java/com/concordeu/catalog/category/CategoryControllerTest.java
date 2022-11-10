package com.concordeu.catalog.category;

import com.concordeu.catalog.controller.CategoryController;
import com.concordeu.catalog.dto.category.CategoryRequestDto;
import com.concordeu.catalog.dto.category.CategoryResponseDto;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.service.category.CategoryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.csrf;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockUser;

@WebFluxTest(controllers = CategoryController.class)
@ActiveProfiles("test")
@Tag("integration")
class CategoryControllerTest {
    @Autowired
    WebTestClient client;
    @MockBean
    CategoryService categoryService;
    @MockBean
    MapStructMapper mapper;

    @Test
    void createCategoryShouldCreateCategory() throws Exception {
        CategoryRequestDto requestDto = new CategoryRequestDto("pc");
        when(mapper.mapCategoryRequestDtoToCategoryDto(requestDto)).thenReturn(new CategoryResponseDto("", "pc", new ArrayList<>()));
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

        verify(categoryService).createCategory(any(CategoryResponseDto.class));
    }

    @Test
    void deleteCategoryShouldDeleteProduct() throws Exception {
        CategoryRequestDto requestDto = new CategoryRequestDto("pc");
        when(mapper.mapCategoryRequestDtoToCategoryDto(requestDto)).thenReturn(new CategoryResponseDto("", "pc", new ArrayList<>()));
        this.client.mutateWith(csrf())
                .mutateWith(mockUser("admin"))
                .delete()
                .uri("/api/v1/catalog/category/delete-category/{categoryName}", "category1")
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
}