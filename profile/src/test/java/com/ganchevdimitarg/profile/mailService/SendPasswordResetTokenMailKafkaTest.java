package com.ganchevdimitarg.profile.mailService;

import com.ganchevdimitarg.profile.base.BaseTest;
import com.ganchevdimitarg.profile.service.MailServiceImpl;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class SendPasswordResetTokenMailKafkaTest extends BaseTest {

    @Autowired
    private MailServiceImpl mailService;

    @Test
    void sendPasswordResetTokenMail_happyPath_messageArrivesOnTopic() {
        // Arrange
        String username = "user@example.com";
        String token = "reset-token-xyz";
        Map<String, Object> consumerProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-group-reset",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        Consumer<String, String> consumer =
                new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer();
        consumer.subscribe(List.of("sentMail"));

        // Act
        StepVerifier.create(mailService.sendPasswordResetTokenMail(username, token))
                .verifyComplete();

        // Assert
        ConsumerRecords<String, String> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
        assertThat(records.count()).isEqualTo(1);
        ConsumerRecord<String, String> temp = records.iterator().next();
        assertThat(temp.topic()).isEqualTo("sentMail");
        assertThat(temp.value()).contains(token);

        consumer.close();
    }

    @Test
    void sendPasswordResetTokenMail_emptyUsername_stillPublishesToTopic() {
        // Arrange
        Map<String, Object> consumerProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-group-empty",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        Consumer<String, String> consumer =
                new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer();
        consumer.subscribe(List.of("sentMail"));

        // Act
        StepVerifier.create(mailService.sendPasswordResetTokenMail("", "token"))
                .verifyComplete();

        // Assert
        ConsumerRecords<String, String> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
        assertThat(records.count()).isEqualTo(1);

        consumer.close();
    }
}