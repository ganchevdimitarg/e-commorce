package com.concordeu.catalog;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Extends the Postgres-only {@link AbstractIntegrationTest} with Redis and Kafka containers.
 * Subclass this for any integration test that exercises caching or event publishing
 * against real infrastructure.
 *
 * <p>Postgres is a singleton managed by the parent; Redis and Kafka are managed per-class by
 * the inherited {@code @Testcontainers} extension, as they are only used by this IT branch.
 */
public abstract class RedisKafkaIntegrationBase extends AbstractIntegrationTest {

    @Container
    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7").withExposedPorts(6379);

    @Container
    protected static final KafkaContainer KAFKA =
            new KafkaContainer("apache/kafka:3.8.1");

    @DynamicPropertySource
    static void redisKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("kafka.bootstrapAddress", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
