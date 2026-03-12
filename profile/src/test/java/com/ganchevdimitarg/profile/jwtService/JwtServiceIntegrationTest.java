package com.ganchevdimitarg.profile.jwtService;

import com.ganchevdimitarg.profile.config.BaseTest;
import com.ganchevdimitarg.profile.config.TestSecurityConfig;
import com.ganchevdimitarg.profile.dao.PasswordResetDao;
import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.Profile;
import com.ganchevdimitarg.profile.service.JwtServiceImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestSecurityConfig.class)
class JwtServiceIntegrationTest extends BaseTest {

    @Autowired
    private JwtServiceImpl jwtService;

    @Autowired
    private ProfileDao profileDao;

    @Autowired
    private PasswordResetDao passwordResetDao;

    private UserDetails testUser;

    @BeforeEach
    void setUp() {
        passwordResetDao.deleteAll().block();
        profileDao.deleteAll().block();

        testUser = new User("dimitar_ganchev", "securePass", Collections.emptyList());
    }

    // -------------------------------------------------------------------------
    // generateToken
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("generateToken: token is persisted and contains correct subject")
    void generateToken_PersistedWithCorrectSubject() {
        String token = jwtService.generateToken(testUser).block();

        assertThat(token).isNotBlank();

        String subject = jwtService.extractClaim(token, Claims::getSubject);
        assertThat(subject).isEqualTo(testUser.getUsername());

        StepVerifier.create(passwordResetDao.findByToken(token).hasElement())
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("generateToken: issuedAt is set and expiration is ~10 minutes later")
    void generateToken_ClaimsContainIssuedAtAndExpiry() {
        jwtService.generateToken(testUser)
                .as(StepVerifier::create)
                .assertNext(token -> {
                    Date issuedAt = jwtService.extractClaim(token, Claims::getIssuedAt);
                    Date expiration = jwtService.extractClaim(token, Claims::getExpiration);

                    assertThat(issuedAt).isNotNull();
                    assertThat(expiration).isAfter(issuedAt);

                    long diffMinutes = Duration.between(
                            issuedAt.toInstant(), expiration.toInstant()
                    ).toMinutes();
                    // Allow small clock drift (8-12 minutes)
                    assertThat(diffMinutes).isBetween(8L, 12L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("generateToken: extra claims are embedded and extractable")
    void generateToken_ExtraClaimsAreEmbedded() {
        Map<String, Object> extraClaims = Map.of("role", "EDITOR", "ip", "127.0.0.1");

        jwtService.generateToken(extraClaims, testUser)
                .as(StepVerifier::create)
                .assertNext(token -> {
                    String role = jwtService.extractClaim(token, c -> c.get("role", String.class));
                    String ip = jwtService.extractClaim(token, c -> c.get("ip", String.class));

                    assertThat(role).isEqualTo("EDITOR");
                    assertThat(ip).isEqualTo("127.0.0.1");
                    assertThat(jwtService.getExpirationMinutes()).isEqualTo(10);
                })
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // saveToken
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("saveToken: null token signals IllegalArgumentException")
    void saveToken_NullToken_SignalsError() {
        StepVerifier.create(jwtService.saveToken(null, testUser.getUsername()))
                .expectErrorMatches(ex ->
                        ex instanceof IllegalArgumentException &&
                                ex.getMessage().equals("Token and username cannot be null"))
                .verify();
    }

    @Test
    @DisplayName("saveToken: null username signals IllegalArgumentException")
    void saveToken_NullUsername_SignalsError() {
        StepVerifier.create(jwtService.saveToken("any.token.value", null))
                .expectErrorMatches(ex ->
                        ex instanceof IllegalArgumentException &&
                                ex.getMessage().equals("Token and username cannot be null"))
                .verify();
    }

    @Test
    @DisplayName("saveToken: both args null signals IllegalArgumentException")
    void saveToken_BothNull_SignalsError() {
        StepVerifier.create(jwtService.saveToken(null, null))
                .expectErrorMatches(ex ->
                        ex instanceof IllegalArgumentException &&
                                ex.getMessage().equals("Token and username cannot be null"))
                .verify();
    }

    // -------------------------------------------------------------------------
    // isTokenValid – happy path
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("isTokenValid: generated token with existing profile returns true")
    void isTokenValid_HappyPath_ReturnsTrue() {
        Profile profile = Profile.builder()
                .username(testUser.getUsername())
                .build();

        Mono<Boolean> pipeline = profileDao.insert(profile)
                .then(jwtService.generateToken(testUser))
                .flatMap(jwtService::isTokenValid);

        StepVerifier.create(pipeline)
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("isTokenValid: only the persisted token is valid among two tokens for the same user")
    void isTokenValid_OnlyPersistedTokenIsValid() {
        // Clean state
        passwordResetDao.deleteAll().block();
        profileDao.deleteAll().block();

        profileDao.insert(Profile.builder().username(testUser.getUsername()).build()).block();

        String persistedToken = jwtService.generateToken(testUser).block();

        String notPersistedToken = Jwts.builder()
                .subject(testUser.getUsername())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(jwtService.getSigningKey())
                .compact();

        StepVerifier.create(jwtService.isTokenValid(persistedToken))
                .expectNext(true)
                .verifyComplete();

        // Remove the persisted token before checking the non-persisted one
        passwordResetDao.deleteAll().block();

        StepVerifier.create(jwtService.isTokenValid(notPersistedToken))
                .expectNext(false)
                .verifyComplete();
    }

    // -------------------------------------------------------------------------
    // isTokenValid – edge cases
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("isTokenValid: no matching profile record returns false")
    void isTokenValid_MissingProfile_ReturnsFalse() {
        // Token is persisted but no profile exists
        Mono<Boolean> pipeline = jwtService.generateToken(testUser)
                .flatMap(jwtService::isTokenValid);

        StepVerifier.create(pipeline)
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("isTokenValid: valid signed token not stored in DB returns false")
    void isTokenValid_SignedButNotPersisted_ReturnsFalse() {
        profileDao.insert(Profile.builder().username(testUser.getUsername()).build()).block();

        String notPersistedToken = Jwts.builder()
                .subject(testUser.getUsername())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(jwtService.getSigningKey())
                .compact();

        StepVerifier.create(jwtService.isTokenValid(notPersistedToken))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("isTokenValid: profile deleted after token generation returns false")
    void isTokenValid_ProfileDeletedAfterGeneration_ReturnsFalse() {
        profileDao.insert(Profile.builder().username(testUser.getUsername()).build()).block();

        String token = jwtService.generateToken(testUser).block();

        profileDao.deleteAll().block();

        StepVerifier.create(jwtService.isTokenValid(token))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("isTokenValid: expired token returns false")
    void isTokenValid_ExpiredToken_ReturnsFalse() {
        String expiredToken = Jwts.builder()
                .subject(testUser.getUsername())
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(jwtService.getSigningKey())
                .compact();

        StepVerifier.create(jwtService.isTokenValid(expiredToken))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("isTokenValid: tampered token signature returns false")
    void isTokenValid_TamperedSignature_ReturnsFalse() {
        String tampered = "eyJhbGciOiJIUzI1NiJ9" +
                ".eyJzdWIiOiJkaW1pdGFyX2dhbmNoZXYiLCJpYXQiOjE3MDk3MzYwMDAsImV4cCI6MjcwOTczNjAwMH0" +
                ".fake-signature";

        StepVerifier.create(jwtService.isTokenValid(tampered))
                .expectNext(false)
                .verifyComplete();
    }
}