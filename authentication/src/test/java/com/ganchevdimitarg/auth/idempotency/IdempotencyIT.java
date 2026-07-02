package com.ganchevdimitarg.auth.idempotency;

import com.ganchevdimitarg.auth.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class IdempotencyIT extends AbstractIntegrationTest {

    private static final String BODY = """
            {"email":"unknown-user@test.io"}""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void should_proceed_when_idempotencyKeyAbsent() throws Exception {
        mvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isAccepted());
    }

    @Test
    void should_returnConflict_when_idempotencyKeyReused() throws Exception {
        String key = "key-" + UUID.randomUUID();

        mvc.perform(post("/api/v1/auth/password-reset")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isAccepted());

        assertThat(redisTemplate.hasKey("auth:idempotency:" + key)).isTrue();

        mvc.perform(post("/api/v1/auth/password-reset")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict());
    }

    // security: register is the headline non-breaking idempotency guarantee — a repeated key
    // must be rejected by the interceptor itself (409), never reach the business-level
    // email-uniqueness check, and the header must remain optional so existing callers still work.

    private String registerBody(String email) {
        return """
                {"email":"%s","password":"Aa1@aaaa","role":"USER",
                 "firstName":"Anna","lastName":"Smith","phoneNumber":"888123456",
                 "city":"Sofia","street":"Main","postCode":"1000"}""".formatted(email);
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void should_succeed_when_registerHasNoIdempotencyKey() throws Exception {
        String email = "nk-" + shortId() + "@test.io";

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated());
    }

    @Test
    void should_returnConflict_when_registerIdempotencyKeyReused() throws Exception {
        String key = "reg-key-" + UUID.randomUUID();
        String emailA = "rka-" + shortId() + "@test.io";
        String emailB = "rkb-" + shortId() + "@test.io";

        mvc.perform(post("/api/v1/auth/register")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(emailA)))
                .andExpect(status().isCreated());

        // Same key, a DIFFERENT (unregistered) email: the 409 must come from the idempotency
        // guard, not from the email-uniqueness check.
        mvc.perform(post("/api/v1/auth/register")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(emailB)))
                .andExpect(status().isConflict());
    }
}
