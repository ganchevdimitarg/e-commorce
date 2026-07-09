package com.ganchevdimitarg.payment.event;

import com.ganchevdimitarg.payment.AbstractIntegrationTest;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end publish against a real Kafka (Testcontainers): the publisher's message lands
 * on {@code payment.charge.completed}, keyed by orderId, with the expected JSON body.
 */
class PaymentEventPublisherIT extends AbstractIntegrationTest {

    @Autowired
    private PaymentEventPublisher publisher;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Test
    void should_landOnTopicKeyedByOrder_when_publishCompleted() {
        String orderId = "order-" + UUID.randomUUID();
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                UUID.randomUUID().toString(), orderId, "ch_1", "cus_1", 500L, "usd", "succeeded", Instant.now());

        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of(PaymentEventPublisher.CHARGE_COMPLETED_TOPIC));

            publisher.publishCompleted(event);

            await().atMost(15, SECONDS).untilAsserted(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                assertThat(records.records(PaymentEventPublisher.CHARGE_COMPLETED_TOPIC))
                        .anySatisfy(record -> {
                            assertThat(record.key()).isEqualTo(orderId);
                            assertThat(record.value())
                                    .contains("\"orderId\":\"" + orderId + "\"")
                                    .contains("\"chargeId\":\"ch_1\"")
                                    .contains("\"status\":\"succeeded\"");
                        });
            });
        }
    }

    private KafkaConsumer<String, String> consumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new KafkaConsumer<>(props);
    }
}
