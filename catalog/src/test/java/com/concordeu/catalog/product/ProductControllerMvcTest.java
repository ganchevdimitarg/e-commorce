package com.concordeu.catalog.product;

import com.concordeu.catalog.config.ResourceServerConfig;
import com.concordeu.catalog.controller.ProductController;
import com.concordeu.catalog.dto.product.ProductResponseDto;
import com.concordeu.catalog.exception.ControllerExceptionHandler;
import com.concordeu.catalog.exception.ProblemAccessDeniedHandler;
import com.concordeu.catalog.exception.ProblemAuthenticationEntryPoint;
import com.concordeu.catalog.mapper.MapStructMapper;
import com.concordeu.catalog.service.product.ProductService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.concordeu.catalog.dto.PageResponse;
import com.concordeu.catalog.dto.product.ItemRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@WebMvcTest(controllers = ProductController.class)
@Import({ResourceServerConfig.class, ControllerExceptionHandler.class,
        ProblemAuthenticationEntryPoint.class, ProblemAccessDeniedHandler.class})
class ProductControllerMvcTest {

    @Autowired
    MockMvc mockMvc;
    @MockitoBean
    ProductService productService;
    @MockitoBean
    MapStructMapper mapper;
    @MockitoBean
    JwtDecoder jwtDecoder;
    @MockitoBean
    CircuitBreakerRegistry circuitBreakerRegistry;
    @MockitoBean
    StringRedisTemplate stringRedisTemplate;

    @Test
    void should_return200WithProduct_when_getProductByNameWithReadScope() throws Exception {
        ProductResponseDto response = new ProductResponseDto(
                "1", "mouse", "WiFi mouse USB", BigDecimal.valueOf(29.99),
                true, "black", null, List.of());
        when(productService.getProductByName("mouse")).thenReturn(response);

        mockMvc.perform(get("/api/v1/catalog/product/get-product")
                        .param("productName", "mouse")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("mouse"))
                .andExpect(jsonPath("$.price").value(29.99));
    }

    @Test
    void should_return200WithProducts_when_getProductsWithReadScope() throws Exception {
        ProductResponseDto dto = new ProductResponseDto(
                "1", "mouse", "WiFi mouse USB", BigDecimal.valueOf(29.99),
                true, "black", null, List.of());
        Page<ProductResponseDto> page = new PageImpl<>(
                List.of(dto), PageRequest.of(0, 20), 1);
        when(productService.getProductsByPage(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/catalog/product/get-products")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("mouse"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void should_return200WithProductsByCategory_when_getCategoryProductsWithReadScope() throws Exception {
        ProductResponseDto dto = new ProductResponseDto(
                "1", "mouse", "WiFi mouse USB", BigDecimal.valueOf(29.99),
                true, "black", null, List.of());
        Page<ProductResponseDto> page = new PageImpl<>(
                List.of(dto), PageRequest.of(0, 20), 1);
        when(productService.getProductsByCategoryByPage(any(), eq("PC"))).thenReturn(page);

        mockMvc.perform(get("/api/v1/catalog/product/get-category-products")
                        .param("categoryName", "PC")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("mouse"));
    }

    @Test
    void should_return200_when_getProductByIdWithReadScope() throws Exception {
        ProductResponseDto response = new ProductResponseDto(
                "123", "mouse", "WiFi mouse USB", BigDecimal.valueOf(29.99),
                true, "black", null, List.of());
        when(productService.getProductById("123")).thenReturn(response);

        mockMvc.perform(get("/api/v1/catalog/product/get-product-id")
                        .param("productId", "123")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("123"));
    }

    @Test
    void should_return200_when_getProductsByIdWithReadScope() throws Exception {
        ProductResponseDto dto = new ProductResponseDto(
                "id1", "mouse", "WiFi mouse USB", BigDecimal.valueOf(29.99),
                true, "black", null, List.of());
        when(productService.getProductsById(any(ItemRequestDto.class))).thenReturn(List.of(dto));

        mockMvc.perform(post("/api/v1/catalog/product/get-products-id")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": ["id1"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("id1"));
    }

    @Test
    void should_return200_when_createProductWithValidBody() throws Exception {
        ProductResponseDto response = new ProductResponseDto(
                "1", "mouse123", "WiFi mouse USB device", BigDecimal.valueOf(29.99),
                true, "black", null, List.of());
        when(productService.createProduct(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/catalog/product/create-product")
                        .param("categoryName", "PC")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "mouse123",
                                    "description": "WiFi mouse USB device",
                                    "price": 29.99,
                                    "inStock": true,
                                    "characteristics": "black"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("mouse123"));
    }

    @Test
    void should_return200_when_updateProductWithValidBody() throws Exception {
        mockMvc.perform(put("/api/v1/catalog/product/update-product")
                        .param("productName", "mouse")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "mouse123",
                                    "description": "updated description text",
                                    "price": 19.99,
                                    "inStock": true,
                                    "characteristics": "black"
                                }
                                """))
                .andExpect(status().isOk());

        verify(productService).updateProduct(eq("mouse"), any());
    }

    @Test
    void should_return200_when_deleteProductWithWriteScope() throws Exception {
        mockMvc.perform(delete("/api/v1/catalog/product/delete-product")
                        .param("productName", "mouse")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write")))
                .andExpect(status().isOk());

        verify(productService).deleteProduct("mouse");
    }

    @Test
    void should_return400ProblemJson_when_createProductWithInvalidBody() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/product/create-product")
                        .param("categoryName", "PC")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "a",
                                    "description": "short",
                                    "price": null,
                                    "inStock": true,
                                    "characteristics": "black"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }
}
