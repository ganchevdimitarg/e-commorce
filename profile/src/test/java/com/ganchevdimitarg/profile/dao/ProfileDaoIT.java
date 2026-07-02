package com.ganchevdimitarg.profile.dao;

import com.ganchevdimitarg.profile.config.BaseTest;
import com.ganchevdimitarg.profile.config.TestSecurityConfig;
import com.ganchevdimitarg.profile.domain.Address;
import com.ganchevdimitarg.profile.domain.Profile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.UUID;

@SpringBootTest
@Import(TestSecurityConfig.class)
class ProfileDaoIT extends BaseTest {

    @Autowired private ProfileDao profileDao;

    @Test
    void should_findActiveByUserId_when_notDeleted() {
        String userId = UUID.randomUUID().toString();
        Profile p = Profile.builder()
                .userId(userId).firstName("Anna").lastName("Smith")
                .phoneNumber("888123456").address(new Address("Sofia", "Main", "1000"))
                .created(LocalDateTime.now()).build();

        StepVerifier.create(profileDao.save(p).then(profileDao.findByUserIdAndDeletedAtIsNull(userId)))
                .expectNextMatches(found -> found.getUserId().equals(userId)
                        && found.getFirstName().equals("Anna"))
                .verifyComplete();
    }
}
