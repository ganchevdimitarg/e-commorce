package com.concordeu.profile.config;

import com.concordeu.client.common.constant.Constant;
import com.concordeu.client.common.dto.AuthUserDto;
import com.concordeu.client.common.dto.NotificationDto;
import com.concordeu.client.common.dto.ReplayPaymentDto;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class KafkaConsumerConfig {

    private final KafkaProducerConfig kafkaProducerConfig;
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapAddress;


    public Map<String, Object> consumerConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
        return props;
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        JsonDeserializer<String> jsonDeserializer = new JsonDeserializer<>();
        jsonDeserializer.addTrustedPackages(Constant.TRUSTED_PACKAGE);
        return new DefaultKafkaConsumerFactory<>(
                consumerConfig(),
                new JsonDeserializer<>(),
                jsonDeserializer);
    }

    @Bean
    public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, String>> messageListener(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplateSimple) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        kafkaTemplateSimple.setObservationEnabled(true);
        factory.setReplyTemplate(kafkaTemplateSimple);
        return factory;
    }

    @Bean
    public ProducerFactory<String, String> producerFactorySimple() {
        return new DefaultKafkaProducerFactory<>(kafkaProducerConfig.producerConfig());
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplateSimple(
            ProducerFactory<String, String> producerFactorySimple) {
        KafkaTemplate<String, String> kafkaTemplate = new KafkaTemplate<>(producerFactorySimple);
        kafkaTemplate.setObservationEnabled(true);
        return kafkaTemplate;
    }
}
