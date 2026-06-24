package com.concordeu.catalog.category;

import com.concordeu.catalog.config.ResourceServerConfig;
import com.concordeu.catalog.controller.CategoryController;
import com.concordeu.catalog.dto.category.CategoryResponseDto;
import com.concordeu.catalog.dto.category.CreateCategoryCommand;
import com.concordeu.catalog.dto.category.MoveProductCommand;
import com.concordeu.catalog.exception.ControllerExceptionHandler;
import com.concordeu.catalog.exception.NotFoundException;
import com.concordeu.catalog.exception.ProblemAccessDeniedHandler;
import com.concordeu.catalog.exception.ProblemAuthenticationEntryPoint;
import com.concordeu.catalog.service.category.CategoryService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    JwtDecoder jwtDecoder;
    @MockitoBean
    CircuitBreakerRegistry circuitBreakerRegistry;
    @MockitoBean
    StringRedisTemplate stringRedisTemplate;
    @MockitoBean
    Tracer tracer; // MdcRequestFilter dependency, not exercised by web-slice tests

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUpRedis() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    @Test
    void should_return200WithCategories_when_getCategoriesWithReadScope() throws Exception {
        CategoryResponseDto dto = new CategoryResponseDto("1", "PC", List.of());
        Page<CategoryResponseDto> page = new PageImpl<>(
                List.of(dto), PageRequest.of(0, 20), 1);
        when(categoryService.getCategoriesByPage(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/catalog/categories")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content[0].name").value("PC"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void should_return201_when_createCategoryWithValidBody() throws Exception {
        CategoryResponseDto response = new CategoryResponseDto("1", "Electronics", List.of());
        when(categoryService.createCategory(any(CreateCategoryCommand.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/catalog/categories")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-1")
                        .content("""
                                {"name": "Electronics"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/catalog/categories/Electronics"))
                .andExpect(jsonPath("$.name").value("Electronics"));
    }

    @Test
    void should_return204_when_deleteCategoryWithWriteScope() throws Exception {
        mockMvc.perform(delete("/api/v1/catalog/categories/PC")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .header("Idempotency-Key", "k-2"))
                .andExpect(status().isNoContent());

        verify(categoryService).deleteCategory("PC");
    }

    @Test
    void should_return200_when_moveOneProductWithWriteScope() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/categories/PC/products/mouse:move")
                        .param("to", "Accessories")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .header("Idempotency-Key", "k-3"))
                .andExpect(status().isOk());

        verify(categoryService).moveOneProduct(new MoveProductCommand("PC", "Accessories", "mouse"));
    }

    @Test
    void should_return200_when_moveAllProductsWithWriteScope() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/categories/PC/products:move-all")
                        .param("to", "Accessories")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .header("Idempotency-Key", "k-4"))
                .andExpect(status().isOk());

        verify(categoryService).moveAllProducts("PC", "Accessories");
    }

    @Test
    void should_return400ProblemJson_when_createCategoryWithEmptyName() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/categories")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-5")
                        .content("""
                                {
                                    "name": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void should_return400ProblemJson_when_createCategoryWithoutIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/categories")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Electronics"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void should_return404ProblemJson_when_deleteCategoryNotFound() throws Exception {
        doThrow(new NotFoundException("Category", "nonexistent"))
                .when(categoryService).deleteCategory(eq("nonexistent"));

        mockMvc.perform(delete("/api/v1/catalog/categories/nonexistent")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .header("Idempotency-Key", "k-404"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Category not found: nonexistent"));
    }
}
