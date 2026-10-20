package com.ganchevdimitarg.catalog.config;

import com.ganchevdimitarg.catalog.event.ProductTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares the product-event topics so they exist before the relay's first send.
 *
 * <p>Partition and replica counts are method parameters with property-backed defaults, which
 * keeps the beans testable without a Spring context.
 *
 * <p>{@code spring.kafka.bootstrap-servers} must be set wherever
 * {@code kafka.bootstrapAddress} is set: Boot's {@code KafkaAdmin}, which creates these
 * topics, honours the former, while the relay's producer honours the latter. Override
 * {@code catalog.kafka.replicas} for multi-broker production clusters; the default of 1 suits
 * local and single-broker environments.
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic productCreatedTopic(
            @Value("${catalog.kafka.partitions:3}") int partitions,
            @Value("${catalog.kafka.replicas:1}") int replicas) {
        return topic(ProductTopics.PRODUCT_CREATED, partitions, replicas);
    }

    @Bean
    public NewTopic productUpdatedTopic(
            @Value("${catalog.kafka.partitions:3}") int partitions,
            @Value("${catalog.kafka.replicas:1}") int replicas) {
        return topic(ProductTopics.PRODUCT_UPDATED, partitions, replicas);
    }

    @Bean
    public NewTopic productDeletedTopic(
            @Value("${catalog.kafka.partitions:3}") int partitions,
            @Value("${catalog.kafka.replicas:1}") int replicas) {
        return topic(ProductTopics.PRODUCT_DELETED, partitions, replicas);
    }

    @Bean
    public NewTopic productCategoryChangedTopic(
            @Value("${catalog.kafka.partitions:3}") int partitions,
            @Value("${catalog.kafka.replicas:1}") int replicas) {
        return topic(ProductTopics.PRODUCT_CATEGORY_CHANGED, partitions, replicas);
    }

    private static NewTopic topic(String name, int partitions, int replicas) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(replicas)
                .build();
    }
}
