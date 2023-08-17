package com.concordeu.catalog.category;

import com.concordeu.catalog.controller.CategoryController;
import com.concordeu.catalog.dto.CategoryDTO;
import com.concordeu.catalog.mapper.CategoryMapper;
import com.concordeu.catalog.service.category.CategoryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.http.protocol.HTTP.CONTENT_TYPE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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
    CategoryMapper mapper;

    @Test
    @WithMockUser(username = "admin")
    void createCategoryShouldCreateCategory() throws Exception {
        this.client
                .mutateWith(csrf())
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
                .expectStatus().isOk()
                .expectHeader().valueEquals(CONTENT_TYPE, APPLICATION_JSON);

        verify(categoryService).createCategory(any(CategoryDTO.class));
    }

    @Test
    void deleteCategoryShouldDeleteProduct() throws Exception {
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
}