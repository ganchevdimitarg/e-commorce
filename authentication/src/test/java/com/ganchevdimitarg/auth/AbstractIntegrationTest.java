package com.ganchevdimitarg.auth;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Base for integration tests backed by real Postgres + Mongo (Testcontainers, singleton pattern).
 * Containers start once in a static initialiser and survive Spring context caching across subclasses.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @ServiceConnection
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7");

    static {
        POSTGRES.start();
        MONGO.start();
    }
}
