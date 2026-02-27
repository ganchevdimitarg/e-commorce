package com.ganchevdimitarg.profile.service;

import com.ganchevdimitarg.profile.dao.PasswordResetDao;
import com.ganchevdimitarg.profile.dao.ProfileDao;
import com.ganchevdimitarg.profile.domain.PasswordReset;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
@Slf4j
@Getter
public class JwtServiceImpl implements JwtService {

    private final PasswordResetDao passwordResetDao;
    private final ProfileDao profileDao;
    private final int expirationMinutes;
    private final SecretKey signingKey;

    public JwtServiceImpl(
            PasswordResetDao passwordResetDao,
            ProfileDao profileDao,
            @Value("${jwt.secret.key}") String secretKey,
            @Value("${jwt.expiration-minutes:10}") int expirationMinutes) {
        this.passwordResetDao = passwordResetDao;
        this.profileDao = profileDao;
        this.expirationMinutes = expirationMinutes;
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
    }

    /**
     * Persist password reset token and its associated username to the store.
     *
     * @param token    the compact JWT string to persist
     * @param username the username associated with the token
     * @return a {@link Mono} completing empty after successful insertion
     */
    @Override
    public Mono<Void> saveToken(String token, String username) {
        return passwordResetDao.insert(PasswordReset.builder()
                        .token(token)
                        .username(username)
                        .createdOn(LocalDateTime.now())
                        .build())
                .then();
    }

    /**
     * Extracts a specific claim from a JWT token using the provided resolver function.
     *
     * @param <T>            the type of the claim value
     * @param token          the compact JWT string to parse
     * @param claimsResolver a function mapping {@link Claims} to the desired value
     * @return the resolved claim value
     * @throws io.jsonwebtoken.JwtException if the token is invalid or expired
     */
    @Override
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    /**
     * Generates a signed JWT token for the given user, persists it to the
     * password reset store, and returns it as a reactive stream.
     * Token expiry is configurable via {@code jwt.expiration-minutes}.
     *
     * @param extraClaims additional claims to embed in the token payload
     * @param userDetails the authenticated user for whom the token is generated
     * @return a {@link Mono} emitting the compact JWT string after it has been persisted
     */
    @Override
    public Mono<String> generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    /**
     * Generates a signed JWT token and persists it
     */
    @Override
    public Mono<String> generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        String token = Jwts.builder()
                .claims().add(extraClaims).and()
                .subject(userDetails.getUsername())
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(signingKey)
                .compact();

        return saveToken(token, userDetails.getUsername())
                .thenReturn(token);
    }

    /**
     * Validates a JWT password reset token by checking:
     * <ul>
     *   <li>The token is not expired</li>
     *   <li>The subject username exists in the profile store</li>
     *   <li>The token exists in the password reset store</li>
     * </ul>
     *
     * @param token the compact JWT string to validate
     * @return a {@link Mono} emitting {@code true} if all checks pass, {@code false} otherwise
     */
    @Override
    public Mono<Boolean> isTokenValid(String token) {
        if (isTokenExpired(token)) {
            return Mono.just(false);
        }
        String username = extractUsername(token);
        // Checks user existence and password reset token validity
        return Mono.zip(
                profileDao.findByUsername(String.valueOf(username)).hasElement(),
                passwordResetDao.findByToken(token).hasElement()
        ).map(tuple -> tuple.getT1() && tuple.getT2());
    }

    private String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(Date.from(Instant.now()));
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}