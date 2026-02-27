package com.ganchevdimitarg.profile.jwtService;

import com.ganchevdimitarg.profile.base.BaseTest;
import com.ganchevdimitarg.profile.dao.PasswordResetDao;
import com.ganchevdimitarg.profile.service.JwtServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class SaveTokenTest extends BaseTest {

    @Autowired
    private JwtServiceImpl jwtService;

    @Autowired
    private PasswordResetDao passwordResetDao;

    @BeforeEach
    void setUp() {
        passwordResetDao.deleteAll().block();
    }

    @Test
    void saveToken_happyPath_persistsTokenWithUsernameAndTimestamp() {
        // Arrange
        String token = "some.jwt.token";
        String username = "user@example.com";

        // Act
        Mono<Void> result = jwtService.saveToken(token, username);

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        StepVerifier.create(passwordResetDao.findByToken(token))
                .assertNext(pr -> {
                    assertThat(pr.getToken()).isEqualTo(token);
                    assertThat(pr.getUsername()).isEqualTo(username);
                    assertThat(pr.getCreatedOn()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    void saveToken_calledTwiceWithSameToken_insertsTwo() {
        // Arrange — idempotency is not enforced at service layer
        String token = "some.jwt.token";
        String username = "user@example.com";

        // Act
        Mono<Void> result = jwtService.saveToken(token, username)
                .then(jwtService.saveToken(token, username));

        // Assert
        StepVerifier.create(result)
                .verifyComplete();

        StepVerifier.create(passwordResetDao.findAllByUsername(username))
                .expectNextCount(2)
                .verifyComplete();
    }
}
