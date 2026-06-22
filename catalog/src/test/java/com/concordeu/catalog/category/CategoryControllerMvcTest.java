package com.concordeu.catalog.category;

import com.concordeu.catalog.config.ResourceServerConfig;
import com.concordeu.catalog.controller.CategoryController;
import com.concordeu.catalog.dto.category.CategoryResponseDto;
import com.concordeu.catalog.exception.ControllerExceptionHandler;
import com.concordeu.catalog.exception.ProblemAccessDeniedHandler;
import com.concordeu.catalog.exception.ProblemAuthenticationEntryPoint;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.service.category.CategoryService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@WebMvcTest(controllers = CategoryController.class)
@Import({ResourceServerConfig.class, ControllerExceptionHandler.class,
        ProblemAuthenticationEntryPoint.class, ProblemAccessDeniedHandler.class})
class CategoryControllerMvcTest {

    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    CategoryService categoryService;
    @MockitoBean
    MapStructMapper mapper;
    @MockitoBean
    JwtDecoder jwtDecoder;
    @MockitoBean
    CircuitBreakerRegistry circuitBreakerRegistry;
    @MockitoBean
    StringRedisTemplate stringRedisTemplate;

    @Test
    void should_return200WithCategories_when_getCategoriesWithReadScope() throws Exception {
        CategoryResponseDto dto = new CategoryResponseDto("1", "PC", List.of());
        Page<CategoryResponseDto> page = new PageImpl<>(
                List.of(dto), PageRequest.of(0, 20), 1);
        when(categoryService.getCategoriesByPage(0, 20)).thenReturn(page);

        mockMvc.perform(get("/api/v1/catalog/category/get-categories")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content[0].name").value("PC"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void should_return200_when_createCategoryWithValidBody() throws Exception {
        CategoryResponseDto response = new CategoryResponseDto("1", "Electronics", List.of());
        when(mapper.mapCategoryRequestDtoToCategoryDto(any())).thenReturn(response);
        when(categoryService.createCategory(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/catalog/category/create-category")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Electronics"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Electronics"));
    }

    @Test
    void should_return200_when_deleteCategoryWithWriteScope() throws Exception {
        mockMvc.perform(delete("/api/v1/catalog/category/delete-category")
                        .param("categoryName", "PC")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write")))
                .andExpect(status().isOk());

        verify(categoryService).deleteCategory("PC");
    }

    @Test
    void should_return200_when_moveOneProductWithWriteScope() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/category/move-one-product")
                        .param("categoryNameFrom", "PC")
                        .param("categoryNameTo", "Accessories")
                        .param("productName", "mouse")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write")))
                .andExpect(status().isOk());

        verify(categoryService).moveOneProduct("PC", "Accessories", "mouse");
    }

    @Test
    void should_return200_when_moveAllProductsWithWriteScope() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/category/move-all-products")
                        .param("categoryNameFrom", "PC")
                        .param("categoryNameTo", "Accessories")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write")))
                .andExpect(status().isOk());

        verify(categoryService).moveAllProducts("PC", "Accessories");
    }

    @Test
    void should_return400ProblemJson_when_createCategoryWithEmptyName() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/category/create-category")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }
}
