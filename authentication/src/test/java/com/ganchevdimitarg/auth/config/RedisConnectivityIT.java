package com.ganchevdimitarg.auth.config;

import com.ganchevdimitarg.auth.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the module can reach a real Redis instance (Testcontainers) via {@link StringRedisTemplate}.
 */
class RedisConnectivityIT extends AbstractIntegrationTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void should_roundTripValue_when_redisAvailable() {
        String key = "authentication:test:redis-connectivity";
        redisTemplate.opsForValue().set(key, "ok");

        String value = redisTemplate.opsForValue().get(key);

        assertThat(value).isEqualTo("ok");
    }
}
