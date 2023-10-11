package com.concordeu.profile.config;

import com.concordeu.client.common.constant.PaymentConstants;
import com.concordeu.client.common.dto.ReplayPaymentDto;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaReplyingProducerConfig {
    public static final String PAYMENT_SERVICE_REPLIES = "paymentServiceReplies";

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
        return new DefaultKafkaProducerFactory<>(producerConfig());
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
                containerFactory.createContainer(PAYMENT_SERVICE_REPLIES);
        repliesContainer.getContainerProperties().setGroupId(PaymentConstants.PAYMENT_SERVICE);
        repliesContainer.setAutoStartup(false);

        return repliesContainer;
    }

    @Bean
    public NewTopic requestsGetCardsByUsername() {
        return TopicBuilder.name(PaymentConstants.GET_CARDS_BY_USERNAME)
                .partitions(10)
                .replicas(2)
                .build();
    }

    @Bean
    public NewTopic requestsAddCardToCustomer() {
        return TopicBuilder.name(PaymentConstants.ADD_CARD_TO_CUSTOMER)
                .partitions(10)
                .replicas(2)
                .build();
    }

    @Bean
    public NewTopic requestsCreateCustomer() {
        return TopicBuilder.name(PaymentConstants.CREATE_CUSTOMER)
                .partitions(10)
                .replicas(2)
                .build();
    }

    @Bean
    public NewTopic requestsDeleteByUsername() {
        return TopicBuilder.name(PaymentConstants.DELETE_BY_USERNAME)
                .partitions(10)
                .replicas(2)
                .build();
    }
}
