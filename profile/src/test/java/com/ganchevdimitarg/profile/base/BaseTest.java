package com.ganchevdimitarg.profile.base;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
public abstract class BaseTest {

    @Container
    protected static final MongoDBContainer mongoDBContainer =
            new MongoDBContainer(DockerImageName.parse("mongo:7.0"))
                    .withReuse(true);

    @Container
    protected static final KafkaContainer kafkaContainer =
            new KafkaContainer("apache/kafka-native:3.8.0")
                    .withReuse(true);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
        registry.add("spring.kafka.bootstrap-servers", kafkaContainer::getBootstrapServers);
    }
}