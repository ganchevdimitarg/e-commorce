package com.ganchevdimitarg.profile.controller;

import com.ganchevdimitarg.profile.base.BaseTest;
import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Profile;
import com.ganchevdimitarg.profile.dto.UserDto;
import com.ganchevdimitarg.profile.dto.UserRequestDto;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.ganchevdimitarg.profile.security.UserRole.ADMIN;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CreateAdminTest extends BaseTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ProfileDao profileDao;

    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        profileDao.deleteAll().block();

        consumer = new DefaultKafkaConsumerFactory<String, String>(
                Map.of(
                        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers(),
                        ConsumerConfig.GROUP_ID_CONFIG, "test-admin-" + UUID.randomUUID(),
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
                )).createConsumer();
        consumer.subscribe(List.of("sentMail"));
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createAdmin_happyPath_returns201AndPersistsProfile() {
        // Arrange
        UserRequestDto request = new UserRequestDto(
                "admin@example.com", "Pass@1234",
                "Ivan", "Ivanov", "+359888000111",
                "Varna", "Main St", "9000",
                null, null, null, null);

        // Act & Assert HTTP
        webTestClient.post()
                .uri("/api/v1/profile/register-admin")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserDto.class)
                .value(dto -> {
                    assertThat(dto.username()).isEqualTo("admin@example.com");
                    assertThat(dto.firstName()).isEqualTo("Ivan");
                    assertThat(dto.lastName()).isEqualTo("Ivanov");
                    assertThat(dto.password()).isEmpty();
                    assertThat(dto.cardId()).isEmpty();
                });

        // Assert profile persisted in MongoDB
        StepVerifier.create(profileDao.findByUsername("admin@example.com"))
                .assertNext(profile -> {
                    assertThat(profile.getUsername()).isEqualTo("admin@example.com");
                    assertThat(profile.getGrantedAuthorities())
                            .containsAll(ADMIN.getGrantedAuthorities()
                                    .stream()
                                    .map(SimpleGrantedAuthority::getAuthority)
                                    .collect(Collectors.toSet()));
                })
                .verifyComplete();

        // Assert Kafka message published
        ConsumerRecords<String, String> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
        assertThat(records.count()).isEqualTo(1);
        assertThat(records.iterator().next().value())
                .contains("admin@example.com")
                .contains("Registration");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createAdmin_duplicateUsername_returns400() {
        // Arrange
        profileDao.insert(Profile.builder()
                .username("admin@example.com")
                .password("encoded")
                .grantedAuthorities(ADMIN.getGrantedAuthorities()
                        .stream()
                        .map(SimpleGrantedAuthority::getAuthority)
                        .collect(Collectors.toSet()))
                .build()).block();

        UserRequestDto request = new UserRequestDto(
                "admin@example.com", "Pass@1234",
                "Ivan", "Ivanov", "+359888000111",
                "Varna", "Main St", "9000",
                null, null, null, null);

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/profile/register-admin")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createAdmin_invalidRequestBody_returns400() {
        // Arrange
        UserRequestDto request = new UserRequestDto(
                "not-an-email", "Pass@1234",
                "Ivan", "Ivanov", "+359888000111",
                "Varna", "Main St", "9000",
                null, null, null, null);

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/profile/register-admin")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void createAdmin_unauthenticated_returns401() {
        // Arrange
        UserRequestDto request = new UserRequestDto(
                "admin@example.com", "Pass@1234",
                "Ivan", "Ivanov", "+359888000111",
                "Varna", "Main St", "9000",
                null, null, null, null);

        // Act & Assert
        webTestClient.post()
                .uri("/api/v1/profile/register-admin")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isUnauthorized();
    }
}