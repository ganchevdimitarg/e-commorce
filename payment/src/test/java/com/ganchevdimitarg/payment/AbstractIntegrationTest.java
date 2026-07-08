package com.ganchevdimitarg.payment;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Base for Postgres- and Redis-backed integration tests. Postgres uses the Testcontainers
 * singleton-container pattern (started once, never stopped by the extension) so the
 * cached Spring context's connection pool stays bound to a live container across
 * subclasses. Never H2. Requires a running Docker engine.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16");

    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
