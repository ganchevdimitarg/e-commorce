package com.ganchevdimitarg.profile.listener;

import com.ganchevdimitarg.profile.config.BaseTest;
import com.ganchevdimitarg.profile.config.TestSecurityConfig;
import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Address;
import com.ganchevdimitarg.profile.domain.Profile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(TestSecurityConfig.class)
class UserDeletedListenerIT extends BaseTest {

    @Autowired private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired private ProfileDao profileDao;

    @Test
    void should_softDeleteProfile_when_userDeletedConsumed() {
        String userId = UUID.randomUUID().toString();
        profileDao.save(Profile.builder().userId(userId).firstName("Anna").lastName("Smith")
                .phoneNumber("888123456").address(new Address("Sofia", "Main", "1000"))
                .created(LocalDateTime.now()).build()).block();

        kafkaTemplate.send("auth.user.deleted", userId,
                "{\"userId\":\"%s\",\"occurredAt\":\"2026-06-26T00:00:00Z\"}".formatted(userId));

        await().atMost(10, SECONDS).untilAsserted(() ->
            assertThat(profileDao.findByUserIdAndDeletedAtIsNull(userId).block()).isNull());
    }
}
