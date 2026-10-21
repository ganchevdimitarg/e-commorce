package com.ganchevdimitarg.catalog.product;

import com.ganchevdimitarg.catalog.config.ResourceServerConfig;
import com.ganchevdimitarg.catalog.controller.ProductController;
import com.ganchevdimitarg.catalog.dto.product.CursorPageResponse;
import com.ganchevdimitarg.catalog.dto.product.ProductResponseDto;
import com.ganchevdimitarg.catalog.dto.product.ProductSearchCriteria;
import com.ganchevdimitarg.catalog.dto.product.UpdateProductRequestDto;
import com.ganchevdimitarg.catalog.exception.ControllerExceptionHandler;
import com.ganchevdimitarg.catalog.exception.PreconditionFailedException;
import com.ganchevdimitarg.catalog.exception.ValidationException;
import com.ganchevdimitarg.catalog.exception.ProblemAccessDeniedHandler;
import com.ganchevdimitarg.catalog.exception.ProblemAuthenticationEntryPoint;
import com.ganchevdimitarg.catalog.mapper.MapStructMapper;
import com.ganchevdimitarg.catalog.service.product.ProductService;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ganchevdimitarg.catalog.exception.NotFoundException;
import com.ganchevdimitarg.catalog.dto.product.ItemRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
                BigDecimal.ONE, true, "", null, List.of(), 0L, 0.0, 0);
        when(productService.createProduct(any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/catalog/products?categoryName=PC")
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-1")
                        .content("""
                                {"name":"mouse","description":"WiFi mouse USB","price":1,"inStock":true,"characteristics":""}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/catalog/products/" + P1))
                .andExpect(header().string(HttpHeaders.ETAG, "\"0\""));
    }

    @Test
    void should_return200WithProduct_when_getProductByNameWithReadScope() throws Exception {
        ProductResponseDto response = new ProductResponseDto(
                P1, "mouse", "WiFi mouse USB", BigDecimal.valueOf(29.99),
                true, "black", null, List.of(), 5L, 4.0, 2);
        when(productService.getProductByName("mouse")).thenReturn(response);

        mockMvc.perform(get("/api/v1/catalog/products/by-name/mouse")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.ETAG, "\"5\""))
                .andExpect(jsonPath("$.name").value("mouse"))
                .andExpect(jsonPath("$.price").value(29.99))
                .andExpect(jsonPath("$.avgStars").value(4.0))
                .andExpect(jsonPath("$.ratingCount").value(2));
    }

    @Test
    void should_return200WithProducts_when_getProductsWithReadScope() throws Exception {
        ProductResponseDto dto = new ProductResponseDto(
                P1, "mouse", "WiFi mouse USB", BigDecimal.valueOf(29.99),
                true, "black", null, List.of(), 0L, 0.0, 0);
        Page<ProductResponseDto> page = new PageImpl<>(
                List.of(dto), PageRequest.of(0, 20), 1);
        when(productService.searchProducts(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/catalog/products")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("mouse"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void should_passFiltersToService_when_queryParamsProvided() throws Exception {
        when(productService.searchProducts(any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/catalog/products")
                        .param("q", "mo")
                        .param("minPrice", "10")
                        .param("maxPrice", "20")
                        .param("inStock", "true")
                        .param("categoryName", "PC")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk());

        ArgumentCaptor<ProductSearchCriteria> captor =
                ArgumentCaptor.forClass(ProductSearchCriteria.class);
        verify(productService).searchProducts(captor.capture(), any());
        ProductSearchCriteria captured = captor.getValue();
        assertThat(captured.q()).isEqualTo("mo");
        assertThat(captured.minPrice()).isEqualByComparingTo("10");
        assertThat(captured.maxPrice()).isEqualByComparingTo("20");
        assertThat(captured.inStock()).isTrue();
        assertThat(captured.categoryName()).isEqualTo("PC");
    }

    @Test
    void should_return400ProblemJson_when_minPriceExceedsMaxPrice() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products")
                        .param("minPrice", "20")
                        .param("maxPrice", "10")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void should_return400ProblemJson_when_filterPriceIsNegative() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products")
                        .param("minPrice", "-1")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void should_return400ProblemJson_when_qExceeds200Characters() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products")
                        .param("q", "a".repeat(201))
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void should_return200_when_qIsBlank() throws Exception {
        when(productService.searchProducts(any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/catalog/products")
                        .param("q", "")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk());

        ArgumentCaptor<ProductSearchCriteria> captor =
                ArgumentCaptor.forClass(ProductSearchCriteria.class);
        verify(productService).searchProducts(captor.capture(), any());
        assertThat(captor.getValue().q()).isNull();
    }

    @Test
    void should_return400ProblemJson_when_categoryNameIsBlank() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products")
                        .param("categoryName", " ")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void should_return400ProblemJson_when_sortPropertyIsNotWhitelisted() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products?sort=doesNotExist")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void should_return200_when_categoryNameProvided() throws Exception {
        ProductResponseDto dto = new ProductResponseDto(
                P1, "mouse", "WiFi mouse USB", BigDecimal.valueOf(29.99),
                true, "black", null, List.of(), 0L, 0.0, 0);
        Page<ProductResponseDto> page = new PageImpl<>(
                List.of(dto), PageRequest.of(0, 20), 1);
        when(productService.searchProducts(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/catalog/products?categoryName=PC")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("mouse"));

        ArgumentCaptor<ProductSearchCriteria> captor =
                ArgumentCaptor.forClass(ProductSearchCriteria.class);
        verify(productService).searchProducts(captor.capture(), any());
        assertThat(captor.getValue().categoryName()).isEqualTo("PC");
    }

    @Test
    void should_returnETag_when_productFetchedById() throws Exception {
        ProductResponseDto response = new ProductResponseDto(
                PROD_123, "mouse", "WiFi mouse USB", BigDecimal.valueOf(29.99),
                true, "black", null, List.of(), 3L, 0.0, 0);
        when(productService.getProductById(PROD_123)).thenReturn(response);

        mockMvc.perform(get("/api/v1/catalog/products/" + PROD_123)
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"3\""))
                .andExpect(jsonPath("$.id").value(PROD_123.toString()))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void should_return200_when_getProductsByIdWithReadScope() throws Exception {
        ProductResponseDto dto = new ProductResponseDto(
                ID_1, "mouse", "WiFi mouse USB", BigDecimal.valueOf(29.99),
                true, "black", null, List.of(), 0L, 0.0, 0);
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
    void should_return400ProblemJson_when_batchGetExceeds100Ids() throws Exception {
        String items = String.join(",", Collections.nCopies(101, "\"" + ID_1 + "\""));

        mockMvc.perform(post("/api/v1/catalog/products/:batch-get")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[" + items + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void should_return400ProblemJson_when_batchGetContainsNullId() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/products/:batch-get")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [null]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void should_return200WithUpdatedProduct_when_updateProductWithValidBody() throws Exception {
        ProductResponseDto updated = new ProductResponseDto(
                PROD_123, "mouse", "WiFi mouse USB v2", BigDecimal.valueOf(19.99),
                false, "black", null, List.of(), 2L, 0.0, 0);
        when(productService.updateProduct(eq(PROD_123), any(), any())).thenReturn(updated);

        mockMvc.perform(put("/api/v1/catalog/products/" + PROD_123)
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-2")
                        .content("""
                                {"description":"WiFi mouse USB v2","price":19.99,"inStock":false,"characteristics":"black"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PROD_123.toString()))
                .andExpect(jsonPath("$.name").value("mouse"))
                .andExpect(jsonPath("$.description").value("WiFi mouse USB v2"))
                .andExpect(jsonPath("$.price").value(19.99));

        verify(productService).updateProduct(eq(PROD_123), any(), any());
    }

    @Test
    void should_updateProduct_when_ifMatchMatches() throws Exception {
        ProductResponseDto updated = new ProductResponseDto(
                PROD_123, "mouse", "WiFi mouse USB v2", BigDecimal.valueOf(19.99),
                false, "black", null, List.of(), 2L, 0.0, 0);
        when(productService.updateProduct(eq(PROD_123), any(), eq(1L))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/catalog/products/" + PROD_123)
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.IF_MATCH, "\"1\"")
                        .header("Idempotency-Key", "k-etag-match")
                        .content("""
                                {"description":"WiFi mouse USB v2","price":19.99,"inStock":false,"characteristics":"black"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"2\""))
                .andExpect(jsonPath("$.version").value(2));

        verify(productService).updateProduct(eq(PROD_123), any(), eq(1L));
    }

    @Test
    void should_return412_when_ifMatchIsStale() throws Exception {
        when(productService.updateProduct(eq(PROD_123), any(), eq(2L)))
                .thenThrow(new PreconditionFailedException("Product", PROD_123));

        mockMvc.perform(put("/api/v1/catalog/products/" + PROD_123)
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.IF_MATCH, "\"2\"")
                        .header("Idempotency-Key", "k-etag-stale")
                        .content("""
                                {"description":"WiFi mouse USB v2","price":19.99,"inStock":false,"characteristics":"black"}
                                """))
                .andExpect(status().isPreconditionFailed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("PRECONDITION_FAILED"));
    }

    @Test
    void should_updateProduct_when_ifMatchIsAbsent() throws Exception {
        ProductResponseDto updated = new ProductResponseDto(
                PROD_123, "mouse", "WiFi mouse USB v2", BigDecimal.valueOf(19.99),
                false, "black", null, List.of(), 2L, 0.0, 0);
        when(productService.updateProduct(eq(PROD_123), any(), isNull())).thenReturn(updated);

        mockMvc.perform(put("/api/v1/catalog/products/" + PROD_123)
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-etag-absent")
                        .content("""
                                {"description":"WiFi mouse USB v2","price":19.99,"inStock":false,"characteristics":"black"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"2\""));

        verify(productService).updateProduct(eq(PROD_123), any(), isNull());
    }

    @Test
    void should_return400_when_ifMatchIsMalformed() throws Exception {
        mockMvc.perform(put("/api/v1/catalog/products/" + PROD_123)
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.IF_MATCH, "not-a-tag")
                        .header("Idempotency-Key", "k-etag-malformed")
                        .content("""
                                {"description":"WiFi mouse USB v2","price":19.99,"inStock":false,"characteristics":"black"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(productService);
    }

    @Test
    void should_acceptWildcardIfMatch_when_productExists() throws Exception {
        ProductResponseDto updated = new ProductResponseDto(
                PROD_123, "mouse", "WiFi mouse USB v2", BigDecimal.valueOf(19.99),
                false, "black", null, List.of(), 2L, 0.0, 0);
        when(productService.updateProduct(eq(PROD_123), any(), isNull())).thenReturn(updated);

        mockMvc.perform(put("/api/v1/catalog/products/" + PROD_123)
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.IF_MATCH, "*")
                        .header("Idempotency-Key", "k-etag-wildcard")
                        .content("""
                                {"description":"WiFi mouse USB v2","price":19.99,"inStock":false,"characteristics":"black"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"2\""));

        verify(productService).updateProduct(eq(PROD_123), any(), isNull());
    }

    @Test
    void should_return404_when_wildcardIfMatchTargetsMissingProduct() throws Exception {
        when(productService.updateProduct(eq(NONEXISTENT), any(), isNull()))
                .thenThrow(new NotFoundException("Product", NONEXISTENT));

        mockMvc.perform(put("/api/v1/catalog/products/" + NONEXISTENT)
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.IF_MATCH, "*")
                        .header("Idempotency-Key", "k-etag-wildcard-missing")
                        .content("""
                                {"description":"WiFi mouse USB v2","price":19.99,"inStock":false,"characteristics":"black"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void should_ignoreName_when_updateRequestIncludesName() throws Exception {
        when(productService.updateProduct(eq(PROD_123), any(), any())).thenReturn(
                new ProductResponseDto(PROD_123, "mouse", "WiFi mouse USB v2",
                        BigDecimal.valueOf(19.99), false, "black", null, List.of(), 2L, 0.0, 0));

        mockMvc.perform(put("/api/v1/catalog/products/" + PROD_123)
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-2b")
                        .content("""
                                {"name":"other-name","description":"WiFi mouse USB v2","price":19.99,"inStock":false,"characteristics":"black"}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<UpdateProductRequestDto> captor =
                ArgumentCaptor.forClass(UpdateProductRequestDto.class);
        verify(mapper).mapProductRequestToUpdateCommand(captor.capture());
        UpdateProductRequestDto captured = captor.getValue();
        assertThat(captured.description()).isEqualTo("WiFi mouse USB v2");
        assertThat(captured.price()).isEqualByComparingTo("19.99");
        assertThat(captured.inStock()).isFalse();
        assertThat(captured.characteristics()).isEqualTo("black");
        verify(productService).updateProduct(eq(PROD_123), any(), any());
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
    void should_return401ProblemJson_when_requestHasNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products/" + PROD_123))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void should_return403ProblemJson_when_updateProductDenied() throws Exception {
        doThrow(new AccessDeniedException("denied"))
                .when(productService).updateProduct(eq(PROD_123), any(), any());

        mockMvc.perform(put("/api/v1/catalog/products/" + PROD_123)
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-denied")
                        .content("""
                                {
                                    "name": "mouse123",
                                    "description": "updated description text",
                                    "price": 19.99,
                                    "inStock": true,
                                    "characteristics": "black"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void should_return409ProblemJson_when_createProductViolatesIntegrity() throws Exception {
        when(productService.createProduct(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        mockMvc.perform(post("/api/v1/catalog/products?categoryName=PC")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-conflict")
                        .content("""
                                {"name":"mouse","description":"WiFi mouse USB","price":1,"inStock":true,"characteristics":""}
                                """))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.detail").value("The request conflicts with existing data"));
    }

    @Test
    void should_return200WithCursorPage_when_cursorEndpointRequested() throws Exception {
        ProductResponseDto dto = new ProductResponseDto(
                P1, "mouse", "WiFi mouse USB", BigDecimal.valueOf(29.99),
                true, "black", null, List.of(), 0L, 0.0, 0);
        when(productService.searchProductsByCursor(any(), isNull(), eq(20), eq("desc")))
                .thenReturn(new CursorPageResponse<>(List.of(dto), "next-token"));

        mockMvc.perform(get("/api/v1/catalog/products/:cursor")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("mouse"))
                .andExpect(jsonPath("$.nextCursor").value("next-token"));

        verify(productService).searchProductsByCursor(any(), isNull(), eq(20), eq("desc"));
    }

    @Test
    void should_passAscDirection_when_cursorSortIsCreatedAtAsc() throws Exception {
        when(productService.searchProductsByCursor(any(), any(), anyInt(), eq("asc")))
                .thenReturn(new CursorPageResponse<>(List.of(), null));

        mockMvc.perform(get("/api/v1/catalog/products/:cursor")
                        .param("sort", "createdAt,asc")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk());

        verify(productService).searchProductsByCursor(any(), any(), anyInt(), eq("asc"));
    }

    @Test
    void should_return400ProblemJson_when_cursorLimitExceeds100() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products/:cursor")
                        .param("limit", "101")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(productService);
    }

    @Test
    void should_return400ProblemJson_when_cursorSortIsUnsupported() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products/:cursor")
                        .param("sort", "price,asc")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(productService);
    }

    @Test
    void should_return400ProblemJson_when_cursorSortHasNoDirection() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products/:cursor")
                        .param("sort", "createdAt")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void should_return400ProblemJson_when_cursorIsTampered() throws Exception {
        when(productService.searchProductsByCursor(any(), eq("tampered"), eq(20), eq("desc")))
                .thenThrow(new ValidationException("Invalid cursor"));

        mockMvc.perform(get("/api/v1/catalog/products/:cursor")
                        .param("cursor", "tampered")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("Invalid cursor"));
    }

    @Test
    void should_keepOffsetPagination_when_cursorEndpointExists() throws Exception {
        ProductResponseDto dto = new ProductResponseDto(
                P1, "mouse", "WiFi mouse USB", BigDecimal.valueOf(29.99),
                true, "black", null, List.of(), 0L, 0.0, 0);
        when(productService.searchProducts(any(), any()))
                .thenReturn(new PageImpl<>(List.of(dto), PageRequest.of(0, 5), 1));

        mockMvc.perform(get("/api/v1/catalog/products")
                        .param("page", "0")
                        .param("size", "5")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("mouse"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
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
