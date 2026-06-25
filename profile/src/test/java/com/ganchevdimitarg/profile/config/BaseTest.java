package com.ganchevdimitarg.profile.config;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
public abstract class BaseTest {

    @Container
    protected static final MongoDBContainer mongoDBContainer =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0"));//.withReplicaSet();

    @Container
    protected static final KafkaContainer kafkaContainer =
            new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:9.6.12");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mongodb.uri", mongoDBContainer::getConnectionString);
        registry.add("spring.mongodb.database", () -> "test");
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
        registry.add("spring.cloud.vault.enabled", () -> "false");
        registry.add("spring.config.import", () -> "optional:vault://");
        registry.add("spring.r2dbc.url", () -> String.format("r2dbc:postgresql://%s:%d/%s",
                postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName()));
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "http://localhost/disabled");
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("jwt.secret.key", () -> "dGhpcy1pcy1hLXZlcnktc2VjdXJlLWtleS13aXRoLWVub3VnaC1ieXRlcy1mb3ItaG1hYw==");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("springdoc.oAuthFlow.authorizationUrl", () -> "http://localhost/oauth2/authorize");
        registry.add("springdoc.oAuthFlow.tokenUrl", () -> "http://localhost/oauth2/token");
    }
}

