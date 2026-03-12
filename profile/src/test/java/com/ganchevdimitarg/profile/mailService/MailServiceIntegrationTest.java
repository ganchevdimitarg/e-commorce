package com.ganchevdimitarg.profile.mailService;

import com.ganchevdimitarg.profile.config.BaseTest;
import com.ganchevdimitarg.profile.dto.NotificationDto;
import com.ganchevdimitarg.profile.service.MailServiceImpl;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MailServiceIntegrationTest extends BaseTest {

    @Autowired
    private MailServiceImpl mailService;

    @Value("${kafka-settings.topics.mail}")
    private String mailTopic;

    @Configuration
    @Import(MailServiceImpl.class)
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.cloud.vault.config.VaultAutoConfiguration",
            "org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration",
            "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration"
    })
    static class TestConfig {

        @Bean
        public ProducerFactory<String, NotificationDto> producerFactory() {
            Map<String, Object> configProps = new HashMap<>();
            // Използваме директно инстанцията от BaseTest
            configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
            configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
            configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.springframework.kafka.support.serializer.JsonSerializer");

            return new DefaultKafkaProducerFactory<>(configProps);
        }

        @Bean
        public KafkaTemplate<String, NotificationDto> kafkaTemplate(ProducerFactory<String, NotificationDto> pf) {
            return new KafkaTemplate<>(pf);
        }
    }


    @Test
    void shouldSendMessageToRealKafkaContainer() {
        var username = "integration@test.com";

        try (var consumer = createTestConsumer()) {
            consumer.subscribe(Collections.singletonList(mailTopic));

            // Act
            StepVerifier.create(mailService.sendUserWelcomeMail(username))
                    .verifyComplete();

            // Assert
            var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
            assertThat(records).isNotEmpty();

            var receivedDto = records.iterator().next().value();
            assertThat(receivedDto.recipient()).isEqualTo(username);
        }
    }

    private Consumer<String, NotificationDto> createTestConsumer() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Пълно избягване на депрекирани методи в Spring 4.x
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.springframework.kafka.support.serializer.ErrorHandlingDeserializer");

        props.put("spring.deserializer.value.delegate.class", "org.springframework.kafka.support.serializer.JsonDeserializer");
        props.put("spring.json.trusted.packages", "*");
        props.put("spring.json.value.default.type", NotificationDto.class.getName());

        return new DefaultKafkaConsumerFactory<String, NotificationDto>(props).createConsumer();
    }
}