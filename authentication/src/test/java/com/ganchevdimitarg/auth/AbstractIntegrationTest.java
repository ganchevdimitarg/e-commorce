package com.ganchevdimitarg.auth;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.util.List;
import java.util.Map;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Base for integration tests backed by real Postgres + Mongo + Kafka (Testcontainers, singleton pattern).
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

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.1");

    static {
        POSTGRES.start();
        MONGO.start();
        KAFKA.start();
    }

    /**
     * Wire Kafka bootstrap-servers via DynamicPropertySource.
     * {@code @ServiceConnection} for {@link KafkaContainer} (native Apache Kafka) may not be
     * auto-registered in Spring Boot 4.1, so we use DynamicPropertySource to be safe.
     */
    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    /**
     * Creates a ready-to-poll String/String consumer subscribed to the given topic.
     * The consumer uses the container's bootstrap address, so it works regardless of
     * whether the application context has started yet.
     *
     * <p>Props are built manually to avoid the deprecated
     * {@code KafkaTestUtils.consumerProps(String, String, String)} 3-arg overload.
     */
    protected static Consumer<String, String> newConsumer(String topic) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + topic,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        );
        Consumer<String, String> consumer =
                new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        consumer.subscribe(List.of(topic));
        return consumer;
    }
}
