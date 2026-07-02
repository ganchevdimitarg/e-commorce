package com.ganchevdimitarg.auth.service;

import com.ganchevdimitarg.auth.dao.PasswordResetTokenRepository;
import com.ganchevdimitarg.auth.dao.UserCredentialRepository;
import com.ganchevdimitarg.auth.domain.PasswordResetToken;
import com.ganchevdimitarg.auth.domain.UserCredential;
import com.ganchevdimitarg.auth.event.PasswordResetRequestedEvent;
import com.ganchevdimitarg.auth.exception.NotFoundException;
import com.ganchevdimitarg.auth.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Token-gated password reset. A reset is proven by presenting a single-use token that
 * expires 15 minutes after issue; the raw token is delivered out-of-band (email via the
 * notification service, driven by an outbox event). Only the SHA-256 hex of the token is
 * persisted, and the raw token is never logged.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private static final int TOKEN_BYTES = 32;
    private static final String INVALID_TOKEN_MESSAGE = "Invalid or expired reset token";

    private final SecureRandom secureRandom = new SecureRandom();

    private final UserCredentialRepository userCredentialRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final OutboxWriter outboxWriter;

    /**
     * Issue a reset token for {@code email} when it maps to a live account, then emit a
     * {@link PasswordResetRequestedEvent} to the outbox. For an unknown email this is a silent
     * no-op so the caller cannot enumerate accounts — the endpoint always answers 202.
     */
    @Transactional
    public void requestReset(String email) {
        UserCredential user = userCredentialRepository.findByEmailAndDeletedAtIsNull(email)
                .orElse(null);
        if (user == null) {
            log.info("Password reset requested for unknown email; ignoring (no enumeration)");
            return;
        }

        String rawToken = generateRawToken();
        String userId = user.getId().toString();
        Instant expiresAt = Instant.now().plus(TOKEN_TTL);

        PasswordResetToken token = new PasswordResetToken();
        token.setId(UUID.randomUUID());
        token.setUserId(user.getId());
        token.setTokenHash(sha256Hex(rawToken));
        token.setExpiresAt(expiresAt);
        tokenRepository.save(token);

        outboxWriter.write(
                AuthTopics.PASSWORD_RESET_REQUESTED, userId,
                new PasswordResetRequestedEvent(userId, user.getEmail(), rawToken, expiresAt, Instant.now()),
                "user", userId);

        log.info("Issued password reset token for user {}", userId);
    }

    /**
     * Consume a reset token and set a new password. Unknown, already-used and expired tokens all
     * fail with the same {@link ValidationException} message so nothing leaks about which case
     * applied. On success the token is stamped used (single-use) and the password re-encoded.
     */
    @Transactional
    public void confirmReset(String token, String newPassword) {
        PasswordResetToken resetToken = tokenRepository
                .findByTokenHashAndUsedAtIsNullAndDeletedAtIsNull(sha256Hex(token))
                .orElseThrow(() -> new ValidationException(INVALID_TOKEN_MESSAGE));

        if (resetToken.getExpiresAt().isBefore(Instant.now())) {
            throw new ValidationException(INVALID_TOKEN_MESSAGE);
        }

        UserCredential user = userCredentialRepository
                .findByIdAndDeletedAtIsNull(resetToken.getUserId())
                .orElseThrow(() -> new NotFoundException("user", resetToken.getUserId()));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userCredentialRepository.save(user);

        resetToken.setUsedAt(Instant.now());
        tokenRepository.save(resetToken);

        log.info("Password reset completed for user {}", user.getId());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
