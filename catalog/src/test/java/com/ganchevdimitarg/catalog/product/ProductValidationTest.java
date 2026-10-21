package com.ganchevdimitarg.catalog.product;

import com.ganchevdimitarg.catalog.config.ResourceServerConfig;
import com.ganchevdimitarg.catalog.controller.ProductController;
import com.ganchevdimitarg.catalog.dto.product.ProductResponseDto;
import com.ganchevdimitarg.catalog.exception.ControllerExceptionHandler;
import com.ganchevdimitarg.catalog.exception.ProblemAccessDeniedHandler;
import com.ganchevdimitarg.catalog.exception.ProblemAuthenticationEntryPoint;
import com.ganchevdimitarg.catalog.mapper.MapStructMapper;
import com.ganchevdimitarg.catalog.ratelimit.RateLimiterCache;
import com.ganchevdimitarg.catalog.service.product.ProductService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@WebMvcTest(controllers = ProductController.class)
@Import({ResourceServerConfig.class, ControllerExceptionHandler.class, ProblemAuthenticationEntryPoint.class, ProblemAccessDeniedHandler.class,
        // The web slice excludes plain @Component beans, so the cache and registry are explicit.
        RateLimiterCache.class, SimpleMeterRegistry.class})
class ProductValidationTest {

    private static final String VALID_NAME = "mouse";
    private static final String VALID_DESCRIPTION = "WiFi mouse USB";
    private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

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
    void should_return400ProblemJson_when_createProductBodyInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/products")
                        .param("categoryName", "PC")
                        .with(csrf())
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "k-val-1")
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

        verifyNoInteractions(productService);
    }

    @Test
    void should_return400ProblemJson_when_pageSizeExceedsMax() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/products")
                        .param("page", "0").param("size", "500")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void should_return400ProblemJson_when_nameIsNull() throws Exception {
        assertRejected(productJson("null", quoted(VALID_DESCRIPTION), "1.00"), "k-val-name-null");
    }

    @Test
    void should_return400ProblemJson_when_nameIsBlank() throws Exception {
        assertRejected(productJson(quoted("   "), quoted(VALID_DESCRIPTION), "1.00"), "k-val-name-blank");
    }

    @Test
    void should_return400ProblemJson_when_nameExceeds200Characters() throws Exception {
        assertRejected(productJson(quoted("a".repeat(201)), quoted(VALID_DESCRIPTION), "1.00"),
                "k-val-name-long");
    }

    @Test
    void should_return201_when_nameIs200Characters() throws Exception {
        assertAccepted(productJson(quoted("a".repeat(200)), quoted(VALID_DESCRIPTION), "1.00"),
                "k-val-name-200");
    }

    @Test
    void should_return400ProblemJson_when_descriptionIsNull() throws Exception {
        assertRejected(productJson(quoted(VALID_NAME), "null", "1.00"), "k-val-desc-null");
    }

    @Test
    void should_return400ProblemJson_when_descriptionIsBlank() throws Exception {
        assertRejected(productJson(quoted(VALID_NAME), quoted("   "), "1.00"), "k-val-desc-blank");
    }

    @Test
    void should_return400ProblemJson_when_descriptionExceeds2000Characters() throws Exception {
        assertRejected(productJson(quoted(VALID_NAME), quoted("d".repeat(2001)), "1.00"),
                "k-val-desc-long");
    }

    @Test
    void should_return201_when_descriptionIs2000Characters() throws Exception {
        assertAccepted(productJson(quoted(VALID_NAME), quoted("d".repeat(2000)), "1.00"),
                "k-val-desc-2000");
    }

    @Test
    void should_return400ProblemJson_when_priceIsNegative() throws Exception {
        assertRejected(productJson(quoted(VALID_NAME), quoted(VALID_DESCRIPTION), "-1.00"),
                "k-val-price-negative");
    }

    @Test
    void should_return400ProblemJson_when_priceHasMoreThanTwoFractionDigits() throws Exception {
        assertRejected(productJson(quoted(VALID_NAME), quoted(VALID_DESCRIPTION), "1.234"),
                "k-val-price-fraction");
    }

    @Test
    void should_return201_when_priceIsZero() throws Exception {
        assertAccepted(productJson(quoted(VALID_NAME), quoted(VALID_DESCRIPTION), "0.00"),
                "k-val-price-zero");
    }

    private static String quoted(String value) {
        return "\"" + value + "\"";
    }

    private static String productJson(String nameJson, String descriptionJson, String priceJson) {
        return """
                {
                    "name": %s,
                    "description": %s,
                    "price": %s,
                    "inStock": true,
                    "characteristics": "black"
                }
                """.formatted(nameJson, descriptionJson, priceJson);
    }

    private ResultActions postProduct(String body, String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/api/v1/catalog/products")
                .param("categoryName", "PC")
                .with(csrf())
                .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", idempotencyKey)
                .content(body));
    }

    private void assertRejected(String body, String idempotencyKey) throws Exception {
        postProduct(body, idempotencyKey)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(productService);
    }

    private void assertAccepted(String body, String idempotencyKey) throws Exception {
        when(productService.createProduct(any())).thenReturn(new ProductResponseDto(
                PRODUCT_ID, VALID_NAME, VALID_DESCRIPTION, BigDecimal.ONE, true, "black", null, List.of(), 0L,
                0.0, 0));

        postProduct(body, idempotencyKey).andExpect(status().isCreated());
    }
}
