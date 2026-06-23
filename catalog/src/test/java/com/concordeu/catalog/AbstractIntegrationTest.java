package com.concordeu.catalog;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Base for Postgres-backed integration tests.
 *
 * <p>Postgres uses the Testcontainers <em>singleton container</em> pattern: it is started
 * once in a static initialiser and never stopped by the JUnit Testcontainers extension.
 * This keeps the shared (cached) Spring context's Hikari pool bound to a live container
 * across all subclasses — a per-class {@code @Container} lifecycle would stop the container
 * after the first IT class finishes, leaving a cached context reused by a later IT pointing
 * at a dead container.
 *
 * <p>{@code @Testcontainers} is kept on the base so that subclasses adding their own
 * per-class {@code @Container} fields (e.g. Redis/Kafka) are still managed by the extension.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }
}
