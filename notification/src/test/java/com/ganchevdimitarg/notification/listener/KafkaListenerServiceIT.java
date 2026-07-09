package com.ganchevdimitarg.notification.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ganchevdimitarg.notification.AbstractIntegrationTest;
import com.ganchevdimitarg.notification.config.KafkaTopics;
import com.ganchevdimitarg.notification.dao.NotificationDao;
import com.ganchevdimitarg.notification.dto.NotificationDto;
import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end: publishes an {@code order.notification.requested} event and asserts the email is
 * sent (GreenMail) and persisted, and that a redelivery with the same key is deduped.
 */
class KafkaListenerServiceIT extends AbstractIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${kafka.bootstrapAddress}")
    private String bootstrap;

    @Autowired
    private NotificationDao notificationDao;

    private void publish(String key, NotificationDto dto) throws Exception {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(
                    KafkaTopics.ORDER_NOTIFICATION_REQUESTED, key, MAPPER.writeValueAsString(dto))).get();
        }
    }

    private long countMessagesTo(String recipient) {
        return Arrays.stream(GREEN_MAIL.getReceivedMessages())
                .filter(m -> hasRecipient(m, recipient))
                .count();
    }

    private boolean hasRecipient(MimeMessage message, String recipient) {
        try {
            Message.RecipientType to = Message.RecipientType.TO;
            var recipients = message.getRecipients(to);
            return recipients != null && Arrays.stream(recipients)
                    .anyMatch(a -> a.toString().equals(recipient));
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void should_sendEmailAndPersist_when_eventReceived() throws Exception {
        NotificationDto dto = new NotificationDto("kafka@test.com", "Hi", "Body from a kafka event");
        publish("evt-1", dto);

        await().atMost(30, SECONDS).untilAsserted(() -> {
            assertThat(countMessagesTo("kafka@test.com")).isEqualTo(1);
            assertThat(notificationDao.findAll())
                    .anyMatch(n -> n.getRecipient().equals("kafka@test.com"));
        });
    }

    @Test
    void should_sendOnce_when_sameKeyDeliveredTwice() throws Exception {
        NotificationDto dto = new NotificationDto("dup@test.com", "Hi", "Duplicate delivery body");
        publish("evt-dup", dto);
        publish("evt-dup", dto);

        await().atMost(30, SECONDS).until(() -> countMessagesTo("dup@test.com") >= 1);
        await().during(3, SECONDS).atMost(10, SECONDS).until(() -> countMessagesTo("dup@test.com") == 1);
    }
}
