package com.ganchevdimitarg.catalog.comment;

import com.ganchevdimitarg.catalog.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves that listing comments by author is limited to the author themselves or an
 * administrator. Any other authenticated reader receives 403 problem+json, so a read scope
 * no longer grants the ability to enumerate another user's reviews.
 */
@Tag("integration")
@AutoConfigureMockMvc
class CommentByAuthorSecurityIT extends AbstractIntegrationTest {

    private static final UUID CATEGORY_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID COMMENT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO categories (id, name, created_at, updated_at, version) "
                + "VALUES (?, ?, now(), now(), 0)", CATEGORY_ID, "by-author-cat");
        jdbc.update("INSERT INTO products (id, name, description, price, stock, characteristics, "
                + "category_id, created_at, updated_at, version, rating_count, avg_stars) "
                + "VALUES (?, ?, 'Product for the by-author security test', 1, true, '', ?, "
                + "now(), now(), 0, 0, 0.0)", PRODUCT_ID, "by-author-product", CATEGORY_ID);
        jdbc.update("INSERT INTO comments (id, author, star, text, title, product_id, "
                + "created_at, updated_at, version) "
                + "VALUES (?, 'alice', 5, 'This is a thorough review text', 'Great product', ?, "
                + "now(), now(), 0)", COMMENT_ID, PRODUCT_ID);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM comments WHERE id = ?", COMMENT_ID);
        jdbc.update("DELETE FROM products WHERE id = ?", PRODUCT_ID);
        jdbc.update("DELETE FROM categories WHERE id = ?", CATEGORY_ID);
    }

    @Test
    void should_return200_when_authorListsOwnComments() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/comments/by-author/alice")
                        .with(jwt().jwt(token -> token.subject("alice"))
                                .authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].author").value("alice"));
    }

    @Test
    void should_return403ProblemJson_whenOtherUserListsComments() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/comments/by-author/alice")
                        .with(jwt().jwt(token -> token.subject("bob"))
                                .authorities(() -> "SCOPE_catalog.read")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void should_return200_whenAdminListsAnotherUsersComments() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/comments/by-author/alice")
                        .with(jwt().jwt(token -> token.subject("admin"))
                                .authorities(() -> "SCOPE_catalog.read", () -> "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].author").value("alice"));
    }
}
