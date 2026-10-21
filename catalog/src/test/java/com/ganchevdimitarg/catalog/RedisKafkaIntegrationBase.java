package com.ganchevdimitarg.catalog;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.kafka.KafkaContainer;

/**
 * Extends the Redis-backed {@link RedisIntegrationBase} with a singleton Kafka broker.
 * Subclass this for any integration test that exercises event publishing against real
 * infrastructure.
 *
 * <p>Kafka is started once in a static initialiser and never stopped by the JUnit extension,
 * matching the Postgres and Redis singletons. Do not annotate it with {@code @Container}:
 * that would stop it after the first IT class and break every cached Spring context that
 * still points at it. {@link org.testcontainers.junit.jupiter.Testcontainers @Testcontainers}
 * is inherited from {@link AbstractIntegrationTest}, so subclasses that still declare their
 * own per-class {@code @Container} fields remain managed by the extension.
 *
 * <p>Reuse ({@code .withReuse(true)}) keeps the broker alive between local test runs, so
 * topics and offsets persist. Consumers created by a test must use a unique group id per
 * class, and assertions must not assume a topic is empty.
 */
public abstract class RedisKafkaIntegrationBase extends RedisIntegrationBase {

    protected static final KafkaContainer KAFKA =
            new KafkaContainer("apache/kafka:3.8.1").withReuse(true);

    static {
        KAFKA.start();
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("kafka.bootstrapAddress", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }
}
