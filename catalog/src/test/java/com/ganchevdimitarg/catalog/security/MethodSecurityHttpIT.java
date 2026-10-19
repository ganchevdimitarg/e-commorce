package com.ganchevdimitarg.catalog.security;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
