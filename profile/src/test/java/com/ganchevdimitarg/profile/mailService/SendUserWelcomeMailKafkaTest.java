package com.ganchevdimitarg.profile.mailService;

import com.ganchevdimitarg.profile.base.BaseTest;
import com.ganchevdimitarg.profile.service.MailServiceImpl;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class SendUserWelcomeMailKafkaTest extends BaseTest {

    @Autowired
    private MailServiceImpl mailService;

    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> consumerProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                kafkaContainer.getBootstrapServers(),  // accessed from BaseIntegrationTest
                ConsumerConfig.GROUP_ID_CONFIG, "test-group-welcome-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        consumer = new DefaultKafkaConsumerFactory<String, String>(consumerProps)
                .createConsumer();
        consumer.subscribe(List.of("sentMail"));
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }


    @Test
    void sendUserWelcomeMail_happyPath_messageArrivesOnTopic() {
        // Arrange
        String username = "user@example.com";

        Map<String, Object> consumerProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        );

        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(
                consumerProps).createConsumer();
        consumer.subscribe(List.of("sentMail"));

        // Act
        StepVerifier.create(mailService.sendUserWelcomeMail(username))
                .verifyComplete();

        // Assert
        ConsumerRecords<String, String> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
        assertThat(records.count()).isEqualTo(1);
        ConsumerRecord<String, String> record = records.iterator().next();
        assertThat(record.topic()).isEqualTo("sentMail");

        consumer.close();
    }
}
