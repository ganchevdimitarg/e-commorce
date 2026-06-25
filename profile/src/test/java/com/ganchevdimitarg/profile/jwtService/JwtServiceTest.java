package com.ganchevdimitarg.profile.jwtService;

import com.ganchevdimitarg.profile.dao.PasswordResetDao;
import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.PasswordReset;
import com.ganchevdimitarg.profile.domain.Profile;
import com.ganchevdimitarg.profile.service.JwtServiceImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import static com.ganchevdimitarg.profile.security.UserRole.USER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {
    private final String SECRET = Base64.getEncoder().encodeToString("super-secret-key-at-least-32-bytes-long-for-hmac".getBytes());

    @Mock
    private PasswordResetDao passwordResetDao;
    @Mock
    private ProfileDao profileDao;

    private JwtServiceImpl jwtService;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtService = new JwtServiceImpl(passwordResetDao, profileDao, SECRET, 10);
        userDetails = new User("testuser", "password", Collections.emptyList());
    }

    private static @NonNull PasswordReset getPasswordReset() {
        return PasswordReset.builder().build();
    }

    @Test
    @DisplayName("Should generate a valid token and save it to DB")
    void generateToken_Success() {
        when(passwordResetDao.insert(any(PasswordReset.class))).thenReturn(Mono.just(getPasswordReset()));

        StepVerifier.create(jwtService.generateToken(userDetails))
                .assertNext(token -> {
                    assertNotNull(token);
                    assertEquals("testuser", jwtService.extractClaim(token, Claims::getSubject));
                })
                .verifyComplete();

        verify(passwordResetDao, times(1)).insert(any(PasswordReset.class));
    }

    @Test
    @DisplayName("isTokenValid returns false when token exists but user does not exist in profileDao")
    void isTokenValid_UserNotFound_ReturnsFalse() {
        when(passwordResetDao.insert(any(PasswordReset.class))).thenReturn(Mono.just(getPasswordReset()));
        String token = generateTestToken();

        when(passwordResetDao.findByToken(token)).thenReturn(Mono.just(getPasswordReset()));
        when(profileDao.findByUsername("testuser")).thenReturn(Mono.empty());

        StepVerifier.create(jwtService.isTokenValid(token))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("extractClaim on expired token throws ExpiredJwtException")
    void extractClaim_expiredToken_throwsExpiredJwtException() {
        String expiredToken = Jwts.builder()
                .subject("testuser")
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(jwtService.getSigningKey())
                .compact();

        assertThrows(ExpiredJwtException.class,
                () -> jwtService.extractClaim(expiredToken, Claims::getSubject));
    }

    @Test
    @DisplayName("Edge Case: tampered token returns false instead of throwing")
    void isTokenValid_TamperedToken_ReturnsFalse() {
        when(passwordResetDao.insert(any(PasswordReset.class))).thenReturn(Mono.just(getPasswordReset()));
        String token = generateTestToken();
        String tamperedToken = token + "modified";

        StepVerifier.create(jwtService.isTokenValid(tamperedToken))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Edge Case: Should return false when token is not found in database")
    void isTokenValid_TokenMissingInDb_ReturnsFalse() {
        when(passwordResetDao.insert(any(PasswordReset.class))).thenReturn(Mono.just(getPasswordReset()));
        String token = generateTestToken();

        // Профилът съществува, но токенът липсва в PasswordResetDao
        when(profileDao.findByUsername(anyString())).thenReturn(Mono.just(mock(com.ganchevdimitarg.profile.domain.Profile.class)));
        when(passwordResetDao.findByToken(token)).thenReturn(Mono.empty());

        StepVerifier.create(jwtService.isTokenValid(token))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("Should extract extra claims correctly")
    void extractClaim_WithExtraClaims_Success() {
        when(passwordResetDao.insert(any(PasswordReset.class))).thenReturn(Mono.just(getPasswordReset()));
        Map<String, Object> extra = Map.of("role", "ADMIN");

        Mono<String> tokenMono = jwtService.generateToken(extra, userDetails);

        StepVerifier.create(tokenMono)
                .assertNext(token -> {
                    String role = jwtService.extractClaim(token, claims -> claims.get("role", String.class));
                    assertEquals("ADMIN", role);
                })
                .verifyComplete();
    }

    @Test
    void saveToken_happyPath_persistsTokenWithUsernameAndTimestamp() {
        // Arrange
        String token = "some.jwt.token";
        String username = "user@example.com";

        when(passwordResetDao.insert(any(PasswordReset.class))).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = jwtService.saveToken(token, username);

        // Assert — verify the reactive chain completes
        StepVerifier.create(result)
                .verifyComplete();

        // Assert — verify insert was called with correct data
        ArgumentCaptor<PasswordReset> captor = ArgumentCaptor.forClass(PasswordReset.class);
        verify(passwordResetDao).insert(captor.capture());

        PasswordReset captured = captor.getValue();
        assertThat(captured.getToken()).isEqualTo(token);
        assertThat(captured.getUsername()).isEqualTo(username);
        assertThat(captured.getCreatedOn()).isNotNull();
    }

    @Test
    void saveToken_calledTwiceWithSameToken_insertsTwo() {
        // Arrange
        String token = "some.jwt.token";
        String username = "user@example.com";

        when(passwordResetDao.insert(any(PasswordReset.class))).thenReturn(Mono.empty());

        // Act
        Mono<Void> result = jwtService.saveToken(token, username)
                .then(jwtService.saveToken(token, username));

        // Assert — reactive chain completes
        StepVerifier.create(result)
                .verifyComplete();

        // Assert — insert was called exactly twice
        verify(passwordResetDao, times(2)).insert(any(PasswordReset.class));
    }

    @Test
    void isTokenValid_happyPath_returnsTrueWhenAllChecksPass() {
        String username = "user@example.com";
        UserDetails user = new User(username, "", USER.getGrantedAuthorities());

        when(passwordResetDao.insert(any(PasswordReset.class))).thenReturn(Mono.empty());
        String token = jwtService.generateToken(user).block();

        when(profileDao.findByUsername(username))
                .thenReturn(Mono.just(Profile.builder().username(username).build()));
        when(passwordResetDao.findByToken(token))
                .thenReturn(Mono.just(PasswordReset.builder().token(token).username(username).build()));

        StepVerifier.create(jwtService.isTokenValid(token))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void isTokenValid_tokenNotInStore_returnsFalse() {
        String token = Jwts.builder()
                .subject("user@example.com")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)))
                .signWith(jwtService.getSigningKey())
                .compact();

        when(profileDao.findByUsername("user@example.com")).thenReturn(Mono.empty());
        when(passwordResetDao.findByToken(token)).thenReturn(Mono.empty());

        StepVerifier.create(jwtService.isTokenValid(token))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void isTokenValid_userNotInProfileStore_returnsFalse() {
        String username = "ghost@example.com";
        UserDetails user = new User(username, "", USER.getGrantedAuthorities());

        // generateToken internally calls saveToken → passwordResetDao.insert
        when(passwordResetDao.insert(any(PasswordReset.class))).thenReturn(Mono.empty());
        String token = jwtService.generateToken(user).block();

        when(profileDao.findByUsername(username)).thenReturn(Mono.empty());
        when(passwordResetDao.findByToken(token))
                .thenReturn(Mono.just(PasswordReset.builder().token(token).username(username).build()));

        StepVerifier.create(jwtService.isTokenValid(token))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void isTokenValid_expiredToken_returnsFalse() {
        // No stubs needed — expiry is caught before any DAO call
        String expiredToken = Jwts.builder()
                .subject("user@example.com")
                .issuedAt(Date.from(Instant.now().minus(20, ChronoUnit.MINUTES)))
                .expiration(Date.from(Instant.now().minus(10, ChronoUnit.MINUTES)))
                .signWith(jwtService.getSigningKey())
                .compact();

        StepVerifier.create(jwtService.isTokenValid(expiredToken))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void generateToken_happyPath_returnsTokenAndPersistsIt() {
        // Arrange
        UserDetails user = new User("user@example.com", "", USER.getGrantedAuthorities());
        when(passwordResetDao.insert(any(PasswordReset.class))).thenReturn(Mono.empty());

        // Act & Assert — token is a valid JWT
        StepVerifier.create(jwtService.generateToken(user))
                .assertNext(token -> {
                    assertThat(token).isNotBlank();
                    assertThat(token.chars().filter(c -> c == '.').count()).isEqualTo(2);
                })
                .verifyComplete();

        // Assert — insert was called with the right username
        ArgumentCaptor<PasswordReset> captor = ArgumentCaptor.forClass(PasswordReset.class);
        verify(passwordResetDao).insert(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("user@example.com");
        assertThat(captor.getValue().getToken()).isNotBlank();
    }

    @Test
    void generateToken_withExtraClaims_embedsClaimsInToken() {
        // Arrange
        UserDetails user = new User("user@example.com", "", USER.getGrantedAuthorities());
        Map<String, Object> extraClaims = Map.of("role", "ADMIN");
        when(passwordResetDao.insert(any(PasswordReset.class))).thenReturn(Mono.empty());

        // Act & Assert — extra claims are embedded in the token
        StepVerifier.create(jwtService.generateToken(extraClaims, user))
                .assertNext(token -> {
                    Claims claims = jwtService.extractClaim(token, c -> c);
                    assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
                })
                .verifyComplete();
    }

    @Test
    void extractClaim_subject_returnsCorrectValue() {
        // Arrange
        String token = buildTokenWithClaims(Map.of());

        // Act
        String subject = jwtService.extractClaim(token, Claims::getSubject);

        // Assert
        assertThat(subject).isEqualTo("user@example.com");
    }

    @Test
    void extractClaim_customClaim_returnsCorrectValue() {
        // Arrange
        String token = buildTokenWithClaims(Map.of("role", "ADMIN"));

        // Act
        String role = jwtService.extractClaim(token, c -> c.get("role", String.class));

        // Assert
        assertThat(role).isEqualTo("ADMIN");
    }

    @Test
    void extractClaim_expirationClaim_returnsCorrectDate() {
        // Arrange
        String token = buildTokenWithClaims(Map.of());
        Date before = Date.from(Instant.now().plus(9, ChronoUnit.MINUTES));
        Date after  = Date.from(Instant.now().plus(11, ChronoUnit.MINUTES));

        // Act
        Date expiration = jwtService.extractClaim(token, Claims::getExpiration);

        // Assert
        assertThat(expiration).isBetween(before, after);
    }

    @Test
    void extractClaim_invalidToken_throwsJwtException() {
        // Arrange
        String invalidToken = "not.a.validtoken";

        // Act & Assert
        assertThatThrownBy(() -> jwtService.extractClaim(invalidToken, Claims::getSubject))
                .isInstanceOf(JwtException.class);
    }

    private String generateTestToken() {
        return jwtService.generateToken(userDetails).block();
    }

    private String buildTokenWithClaims(Map<String, Object> claims) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));
        return Jwts.builder()
                .claims().add(claims).and()
                .subject("user@example.com")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(10, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }
}
