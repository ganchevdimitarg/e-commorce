package com.ganchevdimitarg.catalog.idempotency;

import com.ganchevdimitarg.catalog.RedisIntegrationBase;
import com.ganchevdimitarg.catalog.event.OutboxEventWriter;
import com.ganchevdimitarg.catalog.idempotency.IdempotencyStore.ClaimResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the idempotency contract end to end against a real Redis and the real MVC stack:
 * replay fidelity, key scoping by principal and path, and 409 for an in-progress key.
 */
@Tag("integration")
@AutoConfigureMockMvc
class IdempotencyIT extends RedisIntegrationBase {

    private static final UUID CATEGORY_ID = UUID.fromString("cccccccc-0000-0000-0000-00000000ca11");
    private static final String CATEGORY = "idempotency-cat";
    private static final String CATEGORY_TWO = "idempotency-cat-two";
    private static final String PRODUCTS_URI = "/api/v1/catalog/products";
    private static final String CATEGORIES_URI = "/api/v1/catalog/categories";
    private static final String PRODUCT_JSON = """
            {"name":"mouse","description":"Wireless mouse","price":19.99,
             "inStock":true,"characteristics":"black"}
            """;

    @MockitoBean
    OutboxEventWriter outboxEventWriter;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RedisConnectionFactory redisConnectionFactory;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    IdempotencyStore idempotencyStore;

    @BeforeEach
    void setUp() {
        Objects.requireNonNull(redisConnectionFactory.getConnection()).serverCommands().flushAll();
        deleteCreatedRows();
        jdbc.update("""
                INSERT INTO categories (id, name, created_at, updated_at, version)
                VALUES (?, ?, now(), now(), 0)
                ON CONFLICT (id) DO NOTHING
                """, CATEGORY_ID, CATEGORY);
    }

    @AfterEach
    void tearDown() {
        deleteCreatedRows();
    }

    @Test
    void should_replayIdenticalResponse_when_sameKeyRepeated() throws Exception {
        MvcResult first = mockMvc.perform(post(PRODUCTS_URI)
                        .param("categoryName", CATEGORY)
                        .with(alice())
                        .header("Idempotency-Key", "replay-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_JSON))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = mockMvc.perform(post(PRODUCTS_URI)
                        .param("categoryName", CATEGORY)
                        .with(alice())
                        .header("Idempotency-Key", "replay-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_JSON))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(second.getResponse().getContentAsString())
                .isEqualTo(first.getResponse().getContentAsString());
        assertThat(second.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo(first.getResponse().getHeader(HttpHeaders.LOCATION));
        assertThat(countProducts("mouse")).isEqualTo(1);
    }

    @Test
    void should_executeAgain_when_sameKeyUsedByDifferentPrincipal() throws Exception {
        mockMvc.perform(post(PRODUCTS_URI)
                        .param("categoryName", CATEGORY)
                        .with(alice())
                        .header("Idempotency-Key", "shared-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(post(PRODUCTS_URI)
                        .param("categoryName", CATEGORY)
                        .with(bob())
                        .header("Idempotency-Key", "shared-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"keyboard","description":"Mechanical keyboard","price":49.99,
                                 "inStock":true,"characteristics":"grey"}
                                """))
                .andExpect(status().isCreated());

        assertThat(countProducts("mouse")).isEqualTo(1);
        assertThat(countProducts("keyboard")).isEqualTo(1);
    }

    @Test
    void should_executeAgain_when_keyUsedOnDifferentPath() throws Exception {
        mockMvc.perform(post(PRODUCTS_URI)
                        .param("categoryName", CATEGORY)
                        .with(alice())
                        .header("Idempotency-Key", "path-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(post(CATEGORIES_URI)
                        .with(alice())
                        .header("Idempotency-Key", "path-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + CATEGORY_TWO + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void should_returnConflict_when_requestIsInProgress() throws Exception {
        String redisKey = IdempotencyKeys.scopedKey("alice", "POST", PRODUCTS_URI, "in-progress-key");
        assertThat(idempotencyStore.claim(redisKey)).isInstanceOf(ClaimResult.Claimed.class);

        mockMvc.perform(post(PRODUCTS_URI)
                        .param("categoryName", CATEGORY)
                        .with(alice())
                        .header("Idempotency-Key", "in-progress-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PRODUCT_JSON))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        assertThat(countProducts("mouse")).isZero();
    }

    private static RequestPostProcessor alice() {
        return jwt().jwt(token -> token.subject("alice")).authorities(() -> "SCOPE_catalog.manage");
    }

    private static RequestPostProcessor bob() {
        return jwt().jwt(token -> token.subject("bob")).authorities(() -> "SCOPE_catalog.manage");
    }

    private int countProducts(String name) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM products WHERE name = ?", Integer.class, name);
        return count == null ? 0 : count;
    }

    private void deleteCreatedRows() {
        jdbc.update("DELETE FROM products WHERE name IN ('mouse', 'keyboard')");
        jdbc.update("DELETE FROM categories WHERE name IN (?, ?)", CATEGORY, CATEGORY_TWO);
    }
}
