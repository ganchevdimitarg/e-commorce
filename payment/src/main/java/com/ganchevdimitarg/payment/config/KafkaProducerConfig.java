package com.ganchevdimitarg.payment.config;

import com.ganchevdimitarg.payment.event.PaymentCompletedEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * JSON producer for payment domain events. Mirrors the repo's established pattern
 * (catalog's {@code KafkaProducerConfig}): idempotent producer with {@code acks=all} so a
 * broker retry never duplicates or silently drops a committed event.
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${kafka.bootstrapAddress}")
    private String bootstrapAddress;

    private Map<String, Object> producerConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return props;
    }

    @Bean
    public ProducerFactory<String, PaymentCompletedEvent> paymentEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfig());
    }

    @Bean
    public KafkaTemplate<String, PaymentCompletedEvent> paymentEventKafkaTemplate(
            ProducerFactory<String, PaymentCompletedEvent> paymentEventProducerFactory) {
        KafkaTemplate<String, PaymentCompletedEvent> template = new KafkaTemplate<>(paymentEventProducerFactory);
        template.setObservationEnabled(true);
        return template;
    }
}
