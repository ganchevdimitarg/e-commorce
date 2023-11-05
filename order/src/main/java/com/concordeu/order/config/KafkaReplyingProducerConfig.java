package com.concordeu.order.config;

import com.concordeu.client.common.constant.Constant;
import com.concordeu.client.common.dto.ReplayPaymentDto;
import io.micrometer.core.instrument.ImmutableTag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.checkerframework.checker.units.qual.K;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.MicrometerProducerListener;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.kafka.support.micrometer.KafkaTemplateObservationConvention;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaReplyingProducerConfig {
    public static final String NOTIFICATION_SERVICE_REPLIES = "notificationServiceReplies";

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapAddress;

    public Map<String, Object> producerConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return props;
    }

    @Bean
    public ProducerFactory<String, ReplayPaymentDto> replyingProducerFactory() {
        DefaultKafkaProducerFactory<String, ReplayPaymentDto> defaultKafkaProducerFactory = new DefaultKafkaProducerFactory<>(producerConfig());
        defaultKafkaProducerFactory.addListener(new MicrometerProducerListener<>(new SimpleMeterRegistry(),
                Collections.singletonList(new ImmutableTag("orderReplayingTag", "orderServiceReplayingTag"))));
        return defaultKafkaProducerFactory;
    }

    @Bean
    public ReplyingKafkaTemplate<String, ReplayPaymentDto, String> replyingTemplate(
            ProducerFactory<String, ReplayPaymentDto> pf,
            ConcurrentMessageListenerContainer<String, String> repliesContainer) {

        return new ReplyingKafkaTemplate<>(pf, repliesContainer);
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, String> repliesContainer(
            ConcurrentKafkaListenerContainerFactory<String, String> containerFactory) {

        ConcurrentMessageListenerContainer<String, String> repliesContainer =
                containerFactory.createContainer(NOTIFICATION_SERVICE_REPLIES);
        repliesContainer.getContainerProperties().setGroupId(Constant.PAYMENT_SERVICE);
        repliesContainer.getContainerProperties().setObservationEnabled(true);
        repliesContainer.setAutoStartup(false);

        return repliesContainer;
    }

    @Bean
    public NewTopic requestsGetCardsByUsername() {
        return TopicBuilder.name(Constant.GET_CARDS_BY_USERNAME)
                .partitions(10)
                .replicas(2)
                .build();
    }


}
