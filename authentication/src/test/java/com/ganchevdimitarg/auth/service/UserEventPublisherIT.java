package com.ganchevdimitarg.auth.service;

import com.ganchevdimitarg.auth.AbstractIntegrationTest;
import com.ganchevdimitarg.auth.event.UserRegisteredEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserEventPublisherIT extends AbstractIntegrationTest {

    @Autowired
    private UserEventPublisher publisher;

    @Test
    void should_publishRegisteredEvent_toTopic() {
        var consumer = newConsumer(UserEventPublisher.REGISTERED_TOPIC);
        String userId = UUID.randomUUID().toString();

        publisher.publishRegistered(new UserRegisteredEvent(
                userId, "e@test.io", Set.of("ROLE_USER"),
                "Anna", "Smith", "0888123456", "Sofia", "Main", "1000", Instant.now()));

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(consumer, UserEventPublisher.REGISTERED_TOPIC);
        assertThat(record.value()).contains(userId).contains("e@test.io");
    }
}
