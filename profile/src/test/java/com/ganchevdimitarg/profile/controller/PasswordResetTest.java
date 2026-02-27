package com.ganchevdimitarg.profile.controller;

import com.ganchevdimitarg.profile.base.BaseTest;
import com.ganchevdimitarg.profile.dao.PasswordResetDao;
import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Profile;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ganchevdimitarg.profile.security.UserRole.USER;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PasswordResetTest extends BaseTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ProfileDao profileDao;

    @Autowired
    private PasswordResetDao passwordResetDao;

    @BeforeEach
    void setUp() {
        profileDao.deleteAll().block();
        passwordResetDao.deleteAll().block();
        profileDao.insert(Profile.builder()
                .username("user@example.com")
                .password("encoded")
                .grantedAuthorities(USER.getGrantedAuthorities()
                        .stream()
                        .map(SimpleGrantedAuthority::getAuthority)
                        .collect(Collectors.toSet()))
                .build()).block();
    }

    @Test
    void passwordReset_happyPath_returns204AndPersistsToken() {
        // Act & Assert HTTP response
        webTestClient.post()
                .uri("/api/v1/profile/password-reset?username=user@example.com")
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();

        // Assert token persisted in MongoDB
        StepVerifier.create(passwordResetDao.findByUsername("user@example.com"))
                .assertNext(pr -> {
                    assertThat(pr.getToken()).isNotBlank();
                    assertThat(pr.getUsername()).isEqualTo("user@example.com");
                    assertThat(pr.getCreatedOn()).isNotNull();
                })
                .verifyComplete();

        // Assert Kafka message published with token
        Map<String, Object> consumerProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-password-reset",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        Consumer<String, String> consumer =
                new DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer();
        consumer.subscribe(List.of("sentMail"));
        ConsumerRecords<String, String> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
        assertThat(records.count()).isEqualTo(1);
        assertThat(records.iterator().next().value()).contains("Password reset token");
        consumer.close();
    }

    @Test
    void passwordReset_userNotFound_returns400() {
        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/profile/password-reset?username=ghost@example.com")
                .exchange()
                .expectStatus().isBadRequest();
    }
}
