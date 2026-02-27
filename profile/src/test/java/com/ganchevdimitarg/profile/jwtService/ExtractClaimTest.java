package com.ganchevdimitarg.profile.jwtService;

import com.ganchevdimitarg.profile.dao.PasswordResetDao;
import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.service.JwtServiceImpl;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class ExtractClaimTest {

    private JwtServiceImpl jwtService;

    @Mock
    PasswordResetDao passwordResetDao;
    @Mock
    ProfileDao profileDao;

    private static final String SECRET =
            Base64.getEncoder().encodeToString(new byte[64]);

    @BeforeEach
    void setUp() {
        jwtService = new JwtServiceImpl(passwordResetDao, profileDao, SECRET, 10);
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
}
