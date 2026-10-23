package com.ganchevdimitarg.catalog.security;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the real method-security path: a denied {@code @PreAuthorize} must surface as
 * 403 problem+json, not be swallowed by the catch-all {@code Exception} handler.
 */
@Tag("integration")
@AutoConfigureMockMvc
class MethodSecurityHttpIT extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    StringRedisTemplate stringRedisTemplate;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void stubIdempotencyRedis() {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    @Test
    void should_return403ProblemJson_when_writeEndpointCalledWithReadScope() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/products?categoryName=PC")
                        .with(jwt().authorities(() -> "SCOPE_catalog.read"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "phase1-403")
                        .content("""
                                {"name":"mouse","description":"WiFi mouse USB","price":1,
                                 "inStock":true,"characteristics":""}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void should_return403ProblemJson_when_legacyWriteScopeCallsProductCreate() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/products?categoryName=PC")
                        .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "phase1-legacy-write")
                        .content("""
                                {"name":"mouse","description":"WiFi mouse USB","price":1,
                                 "inStock":true,"characteristics":""}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @ParameterizedTest
    @MethodSource("productAndCategoryMutations")
    void should_return403ProblemJson_when_mutationCalledWithReviewScope(
            MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.with(jwt().authorities(() -> "SCOPE_catalog.review")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    static Stream<MockHttpServletRequestBuilder> productAndCategoryMutations() {
        UUID id = UUID.fromString("99999999-9999-9999-9999-999999999999");
        return Stream.of(
                post("/api/v1/catalog/products?categoryName=PC")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "review-product-create")
                        .content("""
                                {"name":"mouse","description":"WiFi mouse USB","price":1,
                                 "inStock":true,"characteristics":""}
                                """),
                put("/api/v1/catalog/products/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "review-product-update")
                        .content("""
                                {"description":"Updated description text","price":2,
                                 "inStock":true,"characteristics":""}
                                """),
                delete("/api/v1/catalog/products/{id}", id)
                        .header("Idempotency-Key", "review-product-delete"),
                post("/api/v1/catalog/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "review-category-create")
                        .content("""
                                {"name":"Electronics"}
                                """),
                delete("/api/v1/catalog/categories/PC")
                        .header("Idempotency-Key", "review-category-delete"),
                post("/api/v1/catalog/categories/PC/products/mouse:move?to=Other")
                        .header("Idempotency-Key", "review-move-one"),
                post("/api/v1/catalog/categories/PC/products:move-all?to=Other")
                        .header("Idempotency-Key", "review-move-all"));
    }

    @Test
    void should_return403ProblemJson_when_commentCreateCalledWithManageScope() throws Exception {
        mockMvc.perform(post("/api/v1/catalog/products/mouse/comments")
                        .with(jwt().authorities(() -> "SCOPE_catalog.manage"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "manage-comment-create")
                        .content("""
                                {"title":"Great product","text":"This is a thorough review text","star":5}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
