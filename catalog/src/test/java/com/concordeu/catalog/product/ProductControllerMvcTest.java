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
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.concordeu.catalog.exception.NotFoundException;
import com.concordeu.catalog.dto.product.ItemRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
@WebMvcTest(controllers = ProductController.class)
@Import({ResourceServerConfig.class, ControllerExceptionHandler.class,
        ProblemAuthenticationEntryPoint.class, ProblemAccessDeniedHandler.class})
class ProductControllerMvcTest {

    private static final UUID P1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PROD_123 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ID_1 = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID NONEXISTENT = UUID.fromString("77777777-7777-7777-7777-777777777777");

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
    void should_return201AndLocation_when_createProduct() throws Exception {
        ProductResponseDto created = new ProductResponseDto(P1, "mouse", "WiFi mouse USB",
                BigDecimal.ONE, true, "", null, List.of());
        when(productService.createProduct(any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/catalog/products?categoryName=PC")
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-1")
                        .content("""
                                {"name":"mouse","description":"WiFi mouse USB","price":1,"inStock":true,"characteristics":""}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/catalog/products/" + P1));
    }

    @Test
    void should_return200WithProduct_when_getProductByNameWithReadScope() throws Exception {
        ProductResponseDto response = new ProductResponseDto(
                P1, "mouse", "WiFi mouse USB", BigDecimal.valueOf(29.99),
                true, "black", null, List.of());
        when(productService.getProductByName("mouse")).thenReturn(response);

        mockMvc.perform(get("/api/v1/catalog/products/by-name/mouse")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("mouse"))
                .andExpect(jsonPath("$.price").value(29.99));
    }

    @Test
    void should_return200WithProducts_when_getProductsWithReadScope() throws Exception {
        ProductResponseDto dto = new ProductResponseDto(
                P1, "mouse", "WiFi mouse USB", BigDecimal.valueOf(29.99),
                true, "black", null, List.of());
        Page<ProductResponseDto> page = new PageImpl<>(
                List.of(dto), PageRequest.of(0, 20), 1);
        when(productService.getProductsByPage(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/catalog/products")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("mouse"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void should_return200WithProductsByCategory_when_getCategoryProductsWithReadScope() throws Exception {
        ProductResponseDto dto = new ProductResponseDto(
                P1, "mouse", "WiFi mouse USB", BigDecimal.valueOf(29.99),
                true, "black", null, List.of());
        Page<ProductResponseDto> page = new PageImpl<>(
                List.of(dto), PageRequest.of(0, 20), 1);
        when(productService.getProductsByCategoryByPage(any(), eq("PC"))).thenReturn(page);

        mockMvc.perform(get("/api/v1/catalog/products?categoryName=PC")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("mouse"));
    }

    @Test
    void should_return200_when_getProductByIdWithReadScope() throws Exception {
        ProductResponseDto response = new ProductResponseDto(
                PROD_123, "mouse", "WiFi mouse USB", BigDecimal.valueOf(29.99),
                true, "black", null, List.of());
        when(productService.getProductById(PROD_123)).thenReturn(response);

        mockMvc.perform(get("/api/v1/catalog/products/" + PROD_123)
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PROD_123.toString()));
    }

    @Test
    void should_return200_when_getProductsByIdWithReadScope() throws Exception {
        ProductResponseDto dto = new ProductResponseDto(
                ID_1, "mouse", "WiFi mouse USB", BigDecimal.valueOf(29.99),
                true, "black", null, List.of());
        when(productService.getProductsById(any(ItemRequestDto.class))).thenReturn(List.of(dto));

        mockMvc.perform(post("/api/v1/catalog/products/:batch-get")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-batch")
                        .content("""
                                {"items": ["%s"]}
                                """.formatted(ID_1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ID_1.toString()));
    }

    @Test
    void should_return200_when_updateProductWithValidBody() throws Exception {
        mockMvc.perform(put("/api/v1/catalog/products/" + PROD_123)
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-2")
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

        verify(productService).updateProduct(eq(PROD_123), any());
    }

    @Test
    void should_return204_when_deleteProductWithWriteScope() throws Exception {
        mockMvc.perform(delete("/api/v1/catalog/products/" + PROD_123)
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .header("Idempotency-Key", "k-3"))
                .andExpect(status().isNoContent());

        verify(productService).deleteProduct(PROD_123);
    }

    @Test
    void should_return400ProblemJson_when_createProductWithInvalidBody() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/products")
                        .param("categoryName", "PC")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-4")
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

    @Test
    void should_return400ProblemJson_when_createProductWithoutIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/products")
                        .param("categoryName", "PC")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"mouse","description":"WiFi mouse USB","price":1,"inStock":true,"characteristics":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void should_return404ProblemJson_when_getProductByIdNotFound() throws Exception {
        when(productService.getProductById(NONEXISTENT))
                .thenThrow(new NotFoundException("Product", NONEXISTENT));

        mockMvc.perform(get("/api/v1/catalog/products/" + NONEXISTENT)
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Product not found: " + NONEXISTENT));
    }
}
