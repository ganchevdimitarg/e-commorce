package com.concordeu.profile.config;

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
public class KafkaProducerConfig {
    public static final String PAYMENT_SERVICE_REPLIES = "paymentServiceReplies";
    public static final String PAYMENT_SERVICE = "paymentService";
    public static final String SEND_WELCOME_MAIL = "sentWelcomeMail";
    public static final String SEND_PASSWORD_RESET_TOKEN_MAIL = "sendPasswordResetTokenMail";
    public static final String GET_CARDS_BY_USERNAME = "getRequestGetCardsByUsername";
    public static final String ADD_CARD_TO_CUSTOMER = "postRequestAddCardToCustomer";
    public static final String CREATE_CUSTOMER = "postRequestCreateCustomer";
    public static final String CUSTOMER_BY_USERNAME= "deleteRequestCustomerByUsername";

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
    public ProducerFactory<String, ReplayPaymentDto> producerFactory() {
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
        repliesContainer.getContainerProperties().setGroupId(PAYMENT_SERVICE);
        repliesContainer.setAutoStartup(false);

        return repliesContainer;
    }

    @Bean
    public NewTopic kRequests() {
        return TopicBuilder.name(GET_CARDS_BY_USERNAME)
                .partitions(10)
                .replicas(2)
                .build();
    }
}
