package com.ganchevdimitarg.catalog.config;

import com.ganchevdimitarg.catalog.event.ProductTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The topic beans take their partition/replica values as method parameters, so they can be
 * exercised without a Spring context.
 */
@Tag("unit")
class KafkaTopicConfigTest {

    private final KafkaTopicConfig config = new KafkaTopicConfig();

    @Test
    void should_declareProductCreatedTopic() {
        assertTopic(config.productCreatedTopic(3, 1), ProductTopics.PRODUCT_CREATED, 3, 1);
    }

    @Test
    void should_declareProductUpdatedTopic() {
        assertTopic(config.productUpdatedTopic(3, 1), ProductTopics.PRODUCT_UPDATED, 3, 1);
    }

    @Test
    void should_declareProductDeletedTopic() {
        assertTopic(config.productDeletedTopic(3, 1), ProductTopics.PRODUCT_DELETED, 3, 1);
    }

    @Test
    void should_declareProductCategoryChangedTopic() {
        assertTopic(config.productCategoryChangedTopic(3, 1),
                ProductTopics.PRODUCT_CATEGORY_CHANGED, 3, 1);
    }

    @Test
    void should_useConfiguredPartitionsAndReplicas() {
        assertTopic(config.productCreatedTopic(6, 2), ProductTopics.PRODUCT_CREATED, 6, 2);
    }

    private static void assertTopic(NewTopic topic, String name, int partitions, int replicas) {
        assertThat(topic.name()).isEqualTo(name);
        assertThat(topic.numPartitions()).isEqualTo(partitions);
        assertThat(topic.replicationFactor()).isEqualTo((short) replicas);
    }
}
