package com.concordeu.catalog.category;

import com.concordeu.client.catalog.category.CategoryResponseDto;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.controller.CategoryController;
import com.concordeu.client.catalog.category.CategoryRequestDto;
import com.concordeu.catalog.service.category.CategoryService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@WebMvcTest(CategoryController.class)
@Tag("integration")
class CategoryControllerTest {
    @Autowired
    MockMvc mvc;
    @MockBean
    CategoryService categoryService;
    @MockBean
    MapStructMapper mapper;

    @Test
    void createCategoryShouldCreateCategory() throws Exception {
        CategoryRequestDto requestDto = new CategoryRequestDto("pc");
        when(mapper.mapCategoryRequestDtoToCategoryDto(requestDto)).thenReturn(new CategoryResponseDto("","pc",new ArrayList<>()));
        mvc.perform(post("/api/v1/category/create-category")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                    {
                                        "name": "pc"
                                    }
                                """)
                )
                .andExpect(status().isOk());

        verify(categoryService).createCategory(any(CategoryResponseDto.class));
    }

    @Test
    void deleteCategoryShouldDeleteProduct() throws Exception {
        CategoryRequestDto requestDto = new CategoryRequestDto("pc");
        when(mapper.mapCategoryRequestDtoToCategoryDto(requestDto)).thenReturn(new CategoryResponseDto("","pc", new ArrayList<>()));
        mvc.perform(delete("/api/v1/category/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                    {
                                        "name": "pc"
                                    }
                                """))
                .andExpect(status().isOk());

        verify(categoryService).deleteCategory(any());
    }

    @Test
    void moveAllProductsShouldMoveOneProductFromOneCategoryToAnotherCategory() throws Exception {
        mvc.perform(post("/api/v1/category/move-one-product?categoryNameFrom=pc&categoryNameTo=acc&productName=mouse")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(categoryService).moveOneProduct(any(), any(), any());
    }

    @Test
    void moveOneProductShouldMoveAllProductsFromOneCategoryToAnotherCategory() throws Exception {
        mvc.perform(post("/api/v1/category/move-all-products?categoryNameFrom=pc&categoryNameTo=acc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(categoryService).moveAllProducts(any(), any());
    }
}