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
}
