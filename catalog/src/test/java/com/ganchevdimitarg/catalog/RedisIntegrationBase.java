package com.ganchevdimitarg.catalog;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

/**
 * Shared singleton Redis for cache- and idempotency-backed integration tests.
 *
 * <p>The container is started once in a static initialiser and never stopped by the JUnit
 * extension, matching the Postgres singleton in {@link AbstractIntegrationTest}. Do not
 * annotate it with {@code @Container}: that would stop it after the first IT class and
 * break every cached Spring context that still points at it.
 */
public abstract class RedisIntegrationBase extends AbstractIntegrationTest {

    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7").withExposedPorts(6379).withReuse(true);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
