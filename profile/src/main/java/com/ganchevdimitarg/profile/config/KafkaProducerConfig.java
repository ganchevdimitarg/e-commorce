package com.ganchevdimitarg.profile.config;

import com.ganchevdimitarg.profile.dto.NotificationDto;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    private final String bootstrapServers;

    public KafkaProducerConfig(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Bean
    public ProducerFactory<String, NotificationDto> producerFactory() {
        try (JacksonJsonSerializer<NotificationDto> notificationDtoJacksonJsonSerializer = new JacksonJsonSerializer<>()){
            return new DefaultKafkaProducerFactory<>(
                    Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers),
                    new StringSerializer(),
                    notificationDtoJacksonJsonSerializer.noTypeInfo()
            );
        }
    }

    @Bean
    public KafkaTemplate<String, NotificationDto> kafkaTemplate(
            ProducerFactory<String, NotificationDto> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}