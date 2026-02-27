package com.ganchevdimitarg.profile.jwtService;

import com.ganchevdimitarg.profile.base.BaseTest;
import com.ganchevdimitarg.profile.dao.PasswordResetDao;
import com.ganchevdimitarg.profile.service.JwtServiceImpl;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static com.ganchevdimitarg.profile.security.UserRole.USER;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class GenerateTokenTest extends BaseTest {

    @Autowired
    private JwtServiceImpl jwtService;

    @Autowired
    private PasswordResetDao passwordResetDao;

    @BeforeEach
    void setUp() {
        passwordResetDao.deleteAll().block();
    }

    @Test
    void generateToken_happyPath_returnsTokenAndPersistsIt() {
        // Arrange
        UserDetails user = new User("user@example.com", "",
                USER.getGrantedAuthorities());

        // Act
        Mono<String> result = jwtService.generateToken(user);

        // Assert
        StepVerifier.create(result)
                .assertNext(token -> {
                    assertThat(token).isNotBlank();
                    assertThat(token.chars().filter(c -> c == '.').count()).isEqualTo(2);
                })
                .verifyComplete();

        StepVerifier.create(passwordResetDao.findByUsername("user@example.com"))
                .assertNext(pr -> assertThat(pr.getToken()).isNotBlank())
                .verifyComplete();
    }

    @Test
    void generateToken_withExtraClaims_embedsClaimsInToken() {
        // Arrange
        UserDetails user = new User("user@example.com", "", USER.getGrantedAuthorities());
        Map<String, Object> extraClaims = Map.of("role", "ADMIN");

        // Act
        Mono<String> result = jwtService.generateToken(extraClaims, user);

        // Assert
        StepVerifier.create(result)
                .assertNext(token -> {
                    Claims claims = jwtService.extractClaim(token, c -> c);
                    assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
                })
                .verifyComplete();
    }
}