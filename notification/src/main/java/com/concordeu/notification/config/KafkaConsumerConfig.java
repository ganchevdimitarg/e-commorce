package com.concordeu.notification.config;

import com.concordeu.client.common.dto.ReplayPaymentDto;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.MicrometerConsumerListener;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerConfig {
    public static final String TRUSTED_PACKAGE = "com.concordeu.client.common.dto";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapAddress;


    public Map<String, Object> consumerConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
        return props;
    }

    @Bean
    public ConsumerFactory<String, ReplayPaymentDto> consumerFactory() {
        JsonDeserializer<ReplayPaymentDto> jsonDeserializer = new JsonDeserializer<>();
        jsonDeserializer.addTrustedPackages(TRUSTED_PACKAGE);

        DefaultKafkaConsumerFactory<String, ReplayPaymentDto> defaultKafkaConsumerFactory = new DefaultKafkaConsumerFactory<>(
                consumerConfig(),
                new JsonDeserializer<>(),
                jsonDeserializer);
        defaultKafkaConsumerFactory.addListener(new MicrometerConsumerListener<>(meterRegistry(),
                Collections.singletonList(new ImmutableTag("notificationTag", "notificationServiceTag"))));
        return defaultKafkaConsumerFactory;
    }

    @Bean
    public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, ReplayPaymentDto>> messageListener(
            ConsumerFactory<String, ReplayPaymentDto> consumerFactory,
            KafkaTemplate<String, ReplayPaymentDto> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, ReplayPaymentDto> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        kafkaTemplate.setObservationEnabled(true);
        factory.setReplyTemplate(kafkaTemplate);
        return factory;
    }
    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}
