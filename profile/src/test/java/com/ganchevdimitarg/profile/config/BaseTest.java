package com.ganchevdimitarg.profile.config;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton-container base for integration tests backed by MongoDB + Kafka + PostgreSQL.
 *
 * <p>Each container is started once in a static initialiser and remains running for the
 * entire JVM lifetime. Spring's TestContext context-caching is therefore safe: a later
 * IT class that reuses a cached ApplicationContext still sees live containers because
 * they are never stopped between test classes.
 *
 * <p>MongoDB is wired via {@code @ServiceConnection} (Spring Boot 4.x
 * {@code MongoDbContainerConnectionDetailsFactory} creates a {@code MongoConnectionDetails}
 * bean directly from the container). Kafka and PostgreSQL use {@code @DynamicPropertySource}
 * because their service-connection factories require additional context or are not available
 * for the image variants used here.
 *
 * <p>Do NOT annotate containers with {@code @Container} — that hands lifecycle
 * management to JUnit 5, which stops them after each class and breaks context caching.
 */
@ActiveProfiles("test")
public abstract class BaseTest {

    @ServiceConnection
    protected static final MongoDBContainer MONGO =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0"));

    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:9.6.12");

    static {
        MONGO.start();
        KAFKA.start();
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.cloud.vault.enabled", () -> "false");
        registry.add("spring.config.import", () -> "optional:vault://");
        registry.add("spring.r2dbc.url", () -> String.format("r2dbc:postgresql://%s:%d/%s",
                POSTGRES.getHost(), POSTGRES.getFirstMappedPort(), POSTGRES.getDatabaseName()));
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",
                () -> "http://localhost/disabled");
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        registry.add("jwt.secret.key",
                () -> "dGhpcy1pcy1hLXZlcnktc2VjdXJlLWtleS13aXRoLWVub3VnaC1ieXRlcy1mb3ItaG1hYw==");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("springdoc.oAuthFlow.authorizationUrl",
                () -> "http://localhost/oauth2/authorize");
        registry.add("springdoc.oAuthFlow.tokenUrl", () -> "http://localhost/oauth2/token");
    }
}
