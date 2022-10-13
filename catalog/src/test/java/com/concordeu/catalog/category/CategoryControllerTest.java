package com.concordeu.catalog.category;

import com.concordeu.catalog.ModelMapper;
import com.concordeu.catalog.product.ProductController;
import com.concordeu.catalog.product.ProductDto;
import com.concordeu.catalog.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(CategoryController.class)
@Tag("integration")
class CategoryControllerTest {
    CategoryController testController;
    @Autowired
    MockMvc mvc;
    @MockBean
    CategoryService categoryService;

    @BeforeEach
    void setUp() {
        testController = new CategoryController(categoryService);
    }

    @Test
    void createCategoryShouldCreateCategory() throws Exception {
        mvc.perform(post("/api/v1/category/create-category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                    {
                                        "name": "PC"
                                    }
                                """)
                )
                .andExpect(status().isCreated());

        verify(categoryService).createCategory(any(CategoryDto.class));
    }

    @Test
    void deleteCategoryShouldDeleteProduct() throws Exception {
        mvc.perform(delete("/api/v1/category/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                    {
                                        "name": "mouse"
                                    }
                                """))
                .andExpect(status().isAccepted());

        verify(categoryService).deleteCategory(any());
    }

    @Test
    void moveAllProductsShouldMoveOneProductFromOneCategoryToAnotherCategory() throws Exception {
        mvc.perform(post("/api/v1/category/move-one-product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                    {
                                        "categoryFrom": "pc",
                                        "categoryTo": "acc",
                                        "productName": "mouse"
                                    }
                                """))
                .andExpect(status().isMovedPermanently());

        verify(categoryService).moveOneProduct(any(), any(), any());
    }

    @Test
    void moveAllProductsShouldMoveAllProductsFromOneCategoryToAnotherCategory() throws Exception {
        mvc.perform(post("/api/v1/category/move-all-products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                    {
                                        "categoryFrom": "pc",
                                        "categoryTo": "acc"
                                    }
                                """))
                .andExpect(status().isMovedPermanently());

        verify(categoryService).moveAllProducts(any(), any());
    }
}