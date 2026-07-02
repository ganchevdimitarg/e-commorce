package com.ganchevdimitarg.profile.listener;

import com.ganchevdimitarg.profile.config.BaseTest;
import com.ganchevdimitarg.profile.config.TestSecurityConfig;
import com.ganchevdimitarg.profile.dao.ProfileDao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(TestSecurityConfig.class)
class UserRegisteredListenerIT extends BaseTest {

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private ProfileDao profileDao;

    @Test
    void should_createProfileShell_when_userRegisteredConsumed() {
        String userId = UUID.randomUUID().toString();
        kafkaTemplate.send("auth.user.registered", userId, """
            {"userId":"%s","email":"e@test.io","roles":["ROLE_USER"],
             "firstName":"Anna","lastName":"Smith","phoneNumber":"888123456",
             "city":"Sofia","street":"Main","postCode":"1000","occurredAt":"2026-06-26T00:00:00Z"}"""
            .formatted(userId));

        await().atMost(10, SECONDS).untilAsserted(() ->
            assertThat(profileDao.findByUserIdAndDeletedAtIsNull(userId).block()).isNotNull());
    }
}
