package com.ganchevdimitarg.auth.controller;

import com.ganchevdimitarg.auth.AbstractIntegrationTest;
import com.ganchevdimitarg.auth.dao.UserCredentialRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AccountControllerRegisterIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserCredentialRepository repository;

    @Value("${spring.kafka.bootstrap-servers}")
    private String kafkaBootstrapServers;

    private Consumer<String, String> consumer;

    @AfterEach
    void closeConsumer() {
        if (consumer != null) {
            consumer.close();
            consumer = null;
        }
    }

    @Test
    void should_create_credentialAndEmitEvent_when_registerValid() throws Exception {
        // Create isolated consumer positioned at the END of the topic BEFORE the request
        // so only the record we publish is visible when we poll.
        consumer = consumerAtEnd("auth.user.registered");

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                          {"email":"new@test.io","password":"Aa1@aaaa","role":"USER",
                           "firstName":"Anna","lastName":"Smith","phoneNumber":"888123456",
                           "city":"Sofia","street":"Main","postCode":"1000"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").exists());

        assertThat(repository.findByEmailAndDeletedAtIsNull("new@test.io")).isPresent();
        var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        assertThat(records.records("auth.user.registered"))
                .anyMatch(r -> r.value().contains("new@test.io"));
    }

    @Test
    void should_returnConflict_when_emailAlreadyRegistered() throws Exception {
        String body = """
              {"email":"dupe@test.io","password":"Aa1@aaaa","role":"USER",
               "firstName":"Anna","lastName":"Smith","phoneNumber":"888123456",
               "city":"Sofia","street":"Main","postCode":"1000"}""";
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    /**
     * Creates a Kafka consumer directly assigned (not subscribed) to all partitions of {@code topic}
     * and explicitly seeks to the end.  Calling {@link Consumer#position} forces the seek to be
     * applied eagerly so that only records published AFTER this method returns are visible.
     *
     * <p>Using {@code assign} + {@code seekToEnd} instead of {@code subscribe} avoids the
     * lazy-offset-reset race condition: with {@code subscribe + AUTO_OFFSET_RESET=latest}
     * the consumer seeks to the log-end-offset during the first Fetch, which may be AFTER
     * the test action has already published a record — causing the record to be missed.
     */
    private Consumer<String, String> consumerAtEnd(String topic) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "it-register-" + UUID.randomUUID(),
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        );
        Consumer<String, String> c = new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        // Assign partitions directly — no group coordinator, no rebalance delays
        List<TopicPartition> tps = c.partitionsFor(topic).stream()
                .map(p -> new TopicPartition(p.topic(), p.partition()))
                .toList();
        c.assign(tps);
        c.seekToEnd(tps);
        // Force-evaluate the lazy seek so the consumer's fetch position is at the end
        // BEFORE the test action publishes a new record
        tps.forEach(c::position);
        return c;
    }
}
